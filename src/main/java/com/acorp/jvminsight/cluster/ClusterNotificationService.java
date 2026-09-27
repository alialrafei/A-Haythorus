package com.acorp.jvminsight.cluster;

import com.acorp.jvminsight.httpserver.service.SnapshotService;
import com.acorp.jvminsight.notification.NotificationService;
import com.acorp.jvminsight.notification.dto.SavedNotification;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClusterNotificationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterNotificationService.class);
  private final SidecarDiscovery discovery;
  private final SidecarClient client;

  public ClusterNotificationService() {
    this(SidecarDiscoveryFactory.create(), new SidecarClient());
  }

  ClusterNotificationService(SidecarDiscovery discovery, SidecarClient client) {
    this.discovery = discovery;
    this.client = client;
  }

  public List<SavedNotification> getNotifications(Integer requestedShard) {
    List<SavedNotification> notifications =
        requestedShard == null
            ? new ArrayList<>(NotificationService.getInstance().getNotifications())
            : new ArrayList<>();

    List<URI> peers;
    try {
      peers = discovery.discover(requestedShard);
    } catch (Exception ex) {
      LOGGER.warn("Peer discovery failed. Returning local notifications only.", ex);
      return List.copyOf(notifications);
    }

    List<CompletableFuture<List<SavedNotification>>> requests =
        peers.stream()
            .map(peer -> ClusterRequestExecutor.supplyAsync(() -> fetchPeer(peer)))
            .toList();

    for (CompletableFuture<List<SavedNotification>> request : requests) {
      List<SavedNotification> peerNotifications = request.join();
      if (peerNotifications != null) notifications.addAll(peerNotifications);
    }

    return List.copyOf(notifications);
  }

  private List<SavedNotification> fetchPeer(URI peer) {
    try {
      return client.fetchNotifications(peer);
    } catch (Exception ex) {
      LOGGER.warn("Failed to fetch notifications from peer {}.", peer, ex);
      return List.of();
    }
  }
}
