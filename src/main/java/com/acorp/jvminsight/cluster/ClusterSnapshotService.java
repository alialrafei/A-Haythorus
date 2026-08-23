package com.acorp.jvminsight.cluster;

import com.acorp.jvminsight.container.dto.AggregatorSnapshot;
import com.acorp.jvminsight.httpserver.service.SnapshotService;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClusterSnapshotService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterSnapshotService.class);

  /*
   * Peer requests are I/O bound.
   *
   * We're on Java 25, so virtual threads are a very good
   * fit here.
   */
  private static final ExecutorService PEER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

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

    Map<String, AggregatorSnapshot> snapshots = new LinkedHashMap<>();

    /*
     * Always include ourselves.
     */
    AggregatorSnapshot local = SnapshotService.getSnapshot();

    addSnapshot(snapshots, local);

    List<URI> peers;

    try {

      peers = discovery.discover();

      LOGGER.debug("Discovered {} peer sidecar(s): {}", peers.size(), peers);

    } catch (Exception ex) {

      /*
       * Cluster discovery failure must not make the
       * local monitoring API unavailable.
       */
      LOGGER.warn("Peer discovery failed. Returning local snapshot only.", ex);

      return List.copyOf(snapshots.values());
    }

    List<CompletableFuture<AggregatorSnapshot>> requests =
        peers.stream()
            .map(peer -> CompletableFuture.supplyAsync(() -> fetchPeer(peer), PEER_EXECUTOR))
            .toList();

    for (CompletableFuture<AggregatorSnapshot> request : requests) {

      AggregatorSnapshot peerSnapshot = request.join();

      if (peerSnapshot != null) {

        addSnapshot(snapshots, peerSnapshot);
      }
    }

    return List.copyOf(snapshots.values());
  }

  private AggregatorSnapshot fetchPeer(URI peer) {

    try {

      return client.fetchSnapshot(peer);

    } catch (Exception ex) {

      /*
       * One dead sidecar must not break the cluster
       * response.
       */
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
