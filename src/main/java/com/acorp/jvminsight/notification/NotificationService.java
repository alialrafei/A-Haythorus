package com.acorp.jvminsight.notification;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.container.dto.PodInfo;
import com.acorp.jvminsight.container.PodInfoProvider;
import com.acorp.jvminsight.notification.dto.SavedEvidence;
import com.acorp.jvminsight.notification.dto.SavedNotification;
import com.acorp.jvminsight.persistence.FileSystemPersistenceStore;
import com.acorp.jvminsight.persistence.PersistenceStore;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.Recommendation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NotificationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
  private static final NotificationService INSTANCE = new NotificationService();

  private final ConcurrentMap<String, SavedNotification> notifications = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, SavedNotification> dirtyNotifications =
      new ConcurrentHashMap<>();
  private final Deque<SavedEvidence> pendingEvidence = new ArrayDeque<>();
  private final Object stateLock = new Object();
  private final ScheduledExecutorService persistenceExecutor =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "ahaythorus-notification-persistence");
            thread.setDaemon(true);
            return thread;
          });

  private final boolean persistenceEnabled;
  private final int maxInMemoryNotifications;
  private final int maxPendingEvidence;
  private final int maxInstancesPerNotification;
  private final long flushIntervalSeconds;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final int retentionDays;

  private volatile SavedNotificationStore persistentStore;

  private NotificationService() {
    this.persistenceEnabled = ConfigLoader.getBoolean("persistence.enabled", false);
    this.maxInMemoryNotifications =
        Math.max(1, ConfigLoader.getInt("notification.max.in.memory", 1000));
    this.maxPendingEvidence =
        Math.max(1, ConfigLoader.getInt("persistence.max.pending.evidence", 2000));
    this.maxInstancesPerNotification =
        Math.max(1, ConfigLoader.getInt("notification.max.instances.per.notification", 100));
    this.flushIntervalSeconds =
        Math.max(1, ConfigLoader.getLong("persistence.flush.interval.seconds", 30));
    this.retentionDays =
        Math.min(
            10,
            Math.max(1, ConfigLoader.getInt("persistence.retention.days", 10)));

    if (persistenceEnabled) {
      initializePersistence();

      persistenceExecutor.scheduleWithFixedDelay(
          this::safeFlush,
          flushIntervalSeconds,
          flushIntervalSeconds,
          TimeUnit.SECONDS);

      Runtime.getRuntime().addShutdownHook(new Thread(this::safeFlush, "ahaythorus-notification-flush"));
    } else {
      LOGGER.info("Notification persistence is disabled.");
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
    Instant timestamp = snapshot.getTimestamp() == null ? Instant.now() : snapshot.getTimestamp();

    if (snapshot.getDeadlocks() != null && snapshot.getDeadlocks().length > 0) {
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("severity", "CRITICAL");
      evidence.put("threadIds", snapshot.getDeadlocks());
      evidence.put("threads", snapshot.getThreadsInfos());

      record(
          "deadlock",
          snapshot,
          "Deadlock detected: " + snapshot.getDeadlocks().length + " deadlocked thread(s).",
          timestamp,
          "CRITICAL",
          evidence);
    }

    if (delta.getLeakSeverity() != null
        && severityRank(delta.getLeakSeverity().name()) >= severityRank("MEDIUM")) {
      record(
          "jvm-risk",
          snapshot,
          "JVM risk is " + delta.getLeakSeverity().name().toLowerCase() + ".",
          timestamp,
          delta.getLeakSeverity().name(),
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

        record(
            code,
            snapshot,
            message,
            timestamp,
            recommendation.getSeverity() == null ? "UNKNOWN" : recommendation.getSeverity().name(),
            evidence);
      }
    }
  }

  public List<SavedNotification> getNotifications() {
    return notifications.values().stream()
        .sorted(
            Comparator.comparing(
                    (SavedNotification notification) -> latest(notification.getInstances()))
                .reversed())
        .toList();
  }

  public List<SavedEvidence> getEvidence(
      String namespace, String pod, long pid, String id) {
    SavedNotificationStore store = persistentStore;
    if (store == null) {
      return List.of();
    }

    try {
      return store.getEvidence(namespace, pod, pid, id);
    } catch (IOException ex) {
      LOGGER.warn("Failed reading persisted evidence for notification {}.", id, ex);
      return List.of();
    }
  }

  private void record(
      String id,
      JvmSnapshot snapshot,
      String message,
      Instant timestamp,
      String severity,
      Object payload) {

    PodInfo pod = PodInfoProvider.getPodInfo();
    String key = notificationKey(id, severity, message, pod, snapshot.getPid());

    synchronized (stateLock) {
      SavedNotification notification = notifications.get(key);

      if (notification == null) {
        notification =
            new SavedNotification(
                id,
                notificationHash(id, severity, message),
                message,
                severity,
                new ArrayList<>(),
                pod.getNamespace(),
                pod.getName(),
                snapshot.getPid());
        notifications.put(key, notification);
      }

      notification.setMessage(message);
      notification.setSeverity(severity);

      if (notification.getInstances() == null) {
        notification.setInstances(new ArrayList<>());
      }

      notification.getInstances().add(timestamp);
      while (notification.getInstances().size() > maxInstancesPerNotification) {
        notification.getInstances().remove(0);
      }

      dirtyNotifications.put(key, copyNotification(notification));

      if (persistenceEnabled) {
        SavedEvidence evidence =
            new SavedEvidence(
                notification.getHash(),
                id,
                pod.getNamespace(),
                pod.getName(),
                snapshot.getPid(),
                timestamp,
                message,
                payload == null ? null : objectMapper.valueToTree(payload));

        if (pendingEvidence.size() >= maxPendingEvidence) {
          pendingEvidence.pollFirst();
          LOGGER.warn(
              "Notification persistence buffer is full ({}). Dropping oldest pending evidence.",
              maxPendingEvidence);
        }

        pendingEvidence.addLast(evidence);
      }

      trimInMemoryNotifications();
    }
  }

  private void initializePersistence() {
    try {
      String type = ConfigLoader.get("persistence.type", "filesystem").trim().toLowerCase();
      if (!"filesystem".equals(type)) {
        LOGGER.warn(
            "Unsupported persistence.type='{}'. Persistence will be retried but disabled for now.",
            type);
        return;
      }

      Path path = Path.of(ConfigLoader.get("persistence.path", "/data/ahaythorus"));
      PersistenceStore store = new FileSystemPersistenceStore(path);
      persistentStore = new SavedNotificationStore(store);

      List<SavedNotification> loaded = persistentStore.getNotifications();

      synchronized (stateLock) {
        loaded.stream()
            .peek(this::ensureNotificationHash)
            .sorted(
                Comparator.comparing(
                        (SavedNotification notification) -> latest(notification.getInstances()))
                    .reversed())
            .limit(maxInMemoryNotifications)
            .forEach(notification ->
                notifications.put(notificationKey(notification), notification));
      }

      LOGGER.info(
          "Loaded {} persisted notification(s) into memory. Retention={} days, memory limit={}.",
          Math.min(loaded.size(), maxInMemoryNotifications),
          retentionDays,
          maxInMemoryNotifications);
    } catch (Exception ex) {
      persistentStore = null;
      LOGGER.warn(
          "Notification persistence is unavailable. The sidecar will continue without persistence and retry on the next flush cycle.",
          ex);
    }
  }

  private void safeFlush() {
    if (!persistenceEnabled) {
      return;
    }

    try {
      if (persistentStore == null) {
        initializePersistence();
        return;
      }

      flushPersistence();
    } catch (Exception ex) {
      LOGGER.warn("Notification persistence cycle failed. Collector execution will continue.", ex);
    }
  }

  private void flushPersistence() throws IOException {
    SavedNotificationStore store = persistentStore;
    if (store == null) {
      return;
    }

    List<SavedNotification> notificationsToSave;
    List<SavedEvidence> evidenceToSave;

    synchronized (stateLock) {
      notificationsToSave = new ArrayList<>(dirtyNotifications.values());
      evidenceToSave = new ArrayList<>(pendingEvidence);
    }

    for (SavedNotification notification : notificationsToSave) {
      store.saveNotification(notification);
    }

    for (SavedEvidence evidence : evidenceToSave) {
      store.saveEvidence(evidence);
    }

    Instant cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60L * 60L);
    store.deleteExpired(cutoff);

    synchronized (stateLock) {
      dirtyNotifications.keySet().removeAll(
          notificationsToSave.stream()
              .map(this::notificationKey)
              .toList());

      for (SavedEvidence evidence : evidenceToSave) {
        pendingEvidence.remove(evidence);
      }
    }

    LOGGER.debug(
        "Notification persistence flushed: notifications={}, evidence={}.",
        notificationsToSave.size(),
        evidenceToSave.size());
  }

  private void trimInMemoryNotifications() {
    while (notifications.size() > maxInMemoryNotifications) {
      String oldestKey =
          notifications.entrySet().stream()
              .min(
                  Comparator.comparing(
                      entry -> latest(entry.getValue().getInstances())))
              .map(Map.Entry::getKey)
              .orElse(null);

      if (oldestKey == null) {
        return;
      }

      notifications.remove(oldestKey);
    }
  }

  private SavedNotification copyNotification(SavedNotification source) {
    return new SavedNotification(
        source.getId(),
        source.getHash(),
        source.getMessage(),
        source.getSeverity(),
        source.getInstances() == null
            ? new ArrayList<>()
            : new ArrayList<>(source.getInstances()),
        source.getNamespace(),
        source.getPod(),
        source.getPid());
  }

  private String notificationKey(SavedNotification notification) {
    return notification.getNamespace()
        + "/"
        + notification.getPod()
        + ":"
        + notification.getPid()
        + ":"
        + notification.getHash();
  }

  private String notificationKey(
      String id, String severity, String message, PodInfo pod, long pid) {
    return pod.getNamespace()
        + "/"
        + pod.getName()
        + ":"
        + pid
        + ":"
        + notificationHash(id, severity, message);
  }

  private String notificationHash(String id, String severity, String message) {
    String canonical = id + "\u0000" + severity + "\u0000" + message;
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable in this JVM.", ex);
    }
  }

  private void ensureNotificationHash(SavedNotification notification) {
    if (notification.getHash() == null || notification.getHash().isBlank()) {
      notification.setHash(
          notificationHash(
              notification.getId(), notification.getSeverity(), notification.getMessage()));
    }
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
