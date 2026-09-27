package com.acorp.jvminsight.notification;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.container.PodInfo;
import com.acorp.jvminsight.container.PodInfoProvider;
import com.acorp.jvminsight.notification.dto.SavedNotification;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.Recommendation;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NotificationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
  private static final NotificationService INSTANCE = new NotificationService();

  private final ConcurrentMap<String, SavedNotification> notifications = new ConcurrentHashMap<>();
  private final SavedNotificationStore persistentStore;

  private NotificationService() {
    this.persistentStore = createPersistentStore();
    if (persistentStore != null) {
      try {
        persistentStore.getNotifications().forEach(notification ->
            notifications.put(notificationKey(notification), notification));
        LOGGER.info("Loaded {} persisted notification(s).", notifications.size());
      } catch (IOException ex) {
        LOGGER.warn("Failed loading persisted notifications. Starting with empty notification state.", ex);
      }
    }
  }

  public static NotificationService getInstance() {
    return INSTANCE;
  }

  public void record(JvmSnapshot snapshot) {
    if (snapshot == null || snapshot.getDelta() == null) {
      return;
    }

    JvmDeltaSnapshot delta = snapshot.getDelta();
    Instant timestamp = snapshot.getTimestamp() instanceof Instant
        ? (Instant) snapshot.getTimestamp()
        : Instant.now();

    if (snapshot.getDeadlocks() != null && snapshot.getDeadlocks().length > 0) {
      record(
          "deadlock",
          snapshot,
          "Deadlock detected: " + snapshot.getDeadlocks().length + " deadlocked thread(s).",
          timestamp,
          Map.of(
              "severity", "CRITICAL",
              "threadIds", snapshot.getDeadlocks()));
    }

    if (delta.getLeakSeverity() != null
        && severityRank(delta.getLeakSeverity().name()) >= severityRank("MEDIUM")) {
      record(
          "jvm-risk",
          snapshot,
          "JVM risk is " + delta.getLeakSeverity().name().toLowerCase() + ".",
          timestamp,
          Map.of(
              "severity", delta.getLeakSeverity().name(),
              "leakScore", delta.getLeakScore(),
              "instantaneousLeakScore", delta.getInstantaneousLeakScore(),
              "leakReasons", safeList(delta.getLeakReasons())));
    }

    if (delta.getRecommendations() != null) {
      for (Recommendation recommendation : delta.getRecommendations()) {
        if (recommendation == null || recommendation.getTitle() == null) {
          continue;
        }

        String code = "recommendation:" + stableCode(recommendation.getTitle());
        String message =
            recommendation.getDiagnosis() != null
                ? recommendation.getDiagnosis()
                : recommendation.getRecommendation() != null
                    ? recommendation.getRecommendation()
                    : recommendation.getTitle();

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put(
            "severity",
            recommendation.getSeverity() == null ? "UNKNOWN" : recommendation.getSeverity().name());
        evidence.put("confidence", recommendation.getConfidence());
        evidence.put("title", recommendation.getTitle());
        evidence.put("diagnosis", recommendation.getDiagnosis());
        evidence.put("probableCause", recommendation.getProbableCause());
        evidence.put("recommendation", recommendation.getRecommendation());
        evidence.put("evidence", safeList(recommendation.getEvidence()));

        record(code, snapshot, message, timestamp, evidence);
      }
    }
  }

  public List<SavedNotification> getNotifications() {
    return notifications.values().stream()
        .sorted(
            Comparator.comparing(
                    (SavedNotification notification) ->
                        latest(notification.getInstances()))
                .reversed())
        .toList();
  }

  public List<com.acorp.jvminsight.notification.dto.SavedEvidence> getEvidence(String id) {
    if (persistentStore == null) {
      return List.of();
    }
    try {
      return persistentStore.getEvidence(id);
    } catch (IOException ex) {
      LOGGER.warn("Failed reading persisted evidence for notification {}.", id, ex);
      return List.of();
    }
  }

  private void record(
      String id, JvmSnapshot snapshot, String message, Instant timestamp, Object payload) {
    PodInfo pod = PodInfoProvider.getPodInfo();
    SavedNotification current =
        notifications.compute(
            notificationKey(id, pod, snapshot.getPid()),
            (key, existing) -> {
              SavedNotification notification =
                  existing == null
                      ? new SavedNotification(
                          id,
                          message,
                          new ArrayList<>(),
                          pod.namespace(),
                          pod.name(),
                          snapshot.getPid())
                      : existing;
              notification.setMessage(message);
              if (notification.getInstances() == null) {
                notification.setInstances(new ArrayList<>());
              }
              notification.getInstances().add(timestamp);
              return notification;
            });

    if (persistentStore == null) {
      return;
    }

    try {
      persistentStore.saveNotification(current);
      persistentStore.recordEvidence(
          pod.getNamespace(), pod.getName(), snapshot.getPid(), id, message, timestamp, payload);
    } catch (IOException ex) {
      LOGGER.warn("Failed persisting notification {} for pid={}.", id, snapshot.getPid(), ex);
    }
  }

  private SavedNotificationStore createPersistentStore() {
    if (!ConfigLoader.getBoolean("persistence.enabled", false)) {
      LOGGER.info("Notification persistence is disabled.");
      return null;
    }

    String type = ConfigLoader.get("persistence.type", "filesystem").trim().toLowerCase();
    if (!"filesystem".equals(type)) {
      throw new IllegalStateException("Unsupported persistence.type: " + type);
    }

    Path path = Path.of(ConfigLoader.get("persistence.path", "/data/ahaythorus"));
    LOGGER.info("Notification persistence enabled using filesystem path {}.", path);
    return new SavedNotificationStore(new com.acorp.jvminsight.persistence.FileSystemPersistenceStore(path));
  }

  private String notificationKey(SavedNotification notification) {
    return notificationKey(
        notification.getId(),
        podInfo(notification),
        notification.getPid());
  }

  private PodInfo podInfo(SavedNotification notification) {
    PodInfo pod = new PodInfo();
    pod.setName(notification.getPod());
    pod.setNamespace(notification.getNamespace());
    return pod;
  }

  private String notificationKey(String id, PodInfo pod, long pid) {
    return pod.namespace() + "/" + pod.name() + ":" + pid + ":" + id;
  }

  private Instant latest(List<Instant> instances) {
    if (instances == null || instances.isEmpty()) {
      return Instant.EPOCH;
    }
    return instances.get(instances.size() - 1);
  }

  private List<String> safeList(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  private int severityRank(String severity) {
    return switch (severity.toUpperCase()) {
      case "CRITICAL" -> 4;
      case "HIGH" -> 3;
      case "MEDIUM", "WARNING", "WARN" -> 2;
      case "LOW", "INFO" -> 1;
      default -> 0;
    };
  }

  private String stableCode(String title) {
    return title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }
}
