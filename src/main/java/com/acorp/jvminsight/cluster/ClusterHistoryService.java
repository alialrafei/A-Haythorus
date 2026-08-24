package com.acorp.jvminsight.cluster;

import com.acorp.jvminsight.httpserver.service.SnapshotService;
import com.acorp.jvminsight.snapshotcollection.dto.JvmHistoryResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Aggregates bounded JVM history from the local sidecar and discovered peer sidecars. */
public final class ClusterHistoryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterHistoryService.class);
  private static final ExecutorService PEER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  private final SidecarDiscovery discovery;
  private final SidecarClient client;

  public ClusterHistoryService() {
    this(SidecarDiscoveryFactory.create(), new SidecarClient());
  }

  ClusterHistoryService(SidecarDiscovery discovery, SidecarClient client) {
    this.discovery = discovery;
    this.client = client;
  }

  public List<JvmHistoryResponse> getHistories() {
    List<JvmHistoryResponse> histories = new ArrayList<>(SnapshotService.getLocalJvmHistories());

    List<URI> peers;
    try {
      peers = discovery.discover();
    } catch (Exception ex) {
      LOGGER.warn("Peer discovery failed. Returning local JVM history only.", ex);
      return List.copyOf(histories);
    }

    List<CompletableFuture<List<JvmHistoryResponse>>> requests =
        peers.stream()
            .map(peer -> CompletableFuture.supplyAsync(() -> fetchPeer(peer), PEER_EXECUTOR))
            .toList();

    for (CompletableFuture<List<JvmHistoryResponse>> request : requests) {
      List<JvmHistoryResponse> peerHistories = request.join();
      if (peerHistories != null) {
        histories.addAll(peerHistories);
      }
    }

    return List.copyOf(histories);
  }

  private List<JvmHistoryResponse> fetchPeer(URI peer) {
    try {
      return client.fetchHistories(peer);
    } catch (Exception ex) {
      LOGGER.warn("Failed to fetch JVM history from peer {}.", peer, ex);
      return List.of();
    }
  }
}
