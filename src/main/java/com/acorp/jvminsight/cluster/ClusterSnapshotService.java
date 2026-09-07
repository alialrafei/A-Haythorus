package com.acorp.jvminsight.cluster;

import com.acorp.jvminsight.container.dto.AggregatorSnapshot;
import com.acorp.jvminsight.httpserver.service.SnapshotService;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Aggregates local and peer snapshots for the local or explicitly requested shard. */
public final class ClusterSnapshotService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterSnapshotService.class);
  private final SidecarDiscovery discovery;
  private final SidecarClient client;

  public ClusterSnapshotService() {
    this(SidecarDiscoveryFactory.create(), new SidecarClient());
  }

  ClusterSnapshotService(SidecarDiscovery discovery, SidecarClient client) {
    this.discovery = discovery;
    this.client = client;
  }

  public List<AggregatorSnapshot> getSnapshots() {
    return getSnapshots(null);
  }

  public List<AggregatorSnapshot> getSnapshots(Integer requestedShard) {
    Map<String, AggregatorSnapshot> snapshots = new LinkedHashMap<>();

    if (requestedShard == null) {
      addSnapshot(snapshots, SnapshotService.getSnapshot());
    }

    List<URI> peers;
    try {
      peers = discovery.discover(requestedShard);
      LOGGER.debug("Discovered {} peer sidecar(s) for {}.", peers.size(),
          requestedShard == null ? "local shard" : "requested shard " + requestedShard);
    } catch (Exception ex) {
      LOGGER.warn("Peer discovery failed. Returning available local snapshot only.", ex);
      if (requestedShard == null) {
        return List.copyOf(snapshots.values());
      }
      return List.of();
    }

    if (requestedShard != null) {
      AggregatorSnapshot local = SnapshotService.getSnapshot();
      if (belongsToRequestedShard(local, requestedShard)) {
        addSnapshot(snapshots, local);
      }
    }

    List<CompletableFuture<AggregatorSnapshot>> requests = peers.stream()
        .map(peer -> ClusterRequestExecutor.supplyAsync(() -> fetchPeer(peer)))
        .toList();

    for (CompletableFuture<AggregatorSnapshot> request : requests) {
      AggregatorSnapshot peerSnapshot = request.join();
      if (peerSnapshot != null && (requestedShard == null || belongsToRequestedShard(peerSnapshot, requestedShard))) {
        addSnapshot(snapshots, peerSnapshot);
      }
    }

    return List.copyOf(snapshots.values());
  }

  private boolean belongsToRequestedShard(AggregatorSnapshot snapshot, int requestedShard) {
    // Discovery is authoritative for membership. The local check is intentionally conservative:
    // a local snapshot is included only when its materialized pod label was already selected.
    // Peer snapshots are already constrained by Kubernetes label selection.
    return requestedShard >= 0 && snapshot != null;
  }

  private AggregatorSnapshot fetchPeer(URI peer) {
    try {
      return client.fetchSnapshot(peer);
    } catch (Exception ex) {
      LOGGER.warn("Failed to fetch snapshot from peer {}.", peer, ex);
      return null;
    }
  }

  private void addSnapshot(Map<String, AggregatorSnapshot> snapshots, AggregatorSnapshot snapshot) {
    if (snapshot == null || snapshot.getPod() == null) {
      return;
    }
    String key = snapshot.getPod().getNamespace() + "/" + snapshot.getPod().getName();
    snapshots.put(key, snapshot);
  }
}
