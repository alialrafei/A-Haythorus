package com.acorp.jvminsight.notification;

import com.acorp.jvminsight.notification.dto.SavedEvidence;
import com.acorp.jvminsight.notification.dto.SavedNotification;
import com.acorp.jvminsight.persistence.PersistenceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class SavedNotificationStore {

  private static final String NOTIFICATION_PREFIX = "notifications/";
  private static final String EVIDENCE_PREFIX = "evidence/";

  private final PersistenceStore store;
  private final ObjectMapper mapper;

  public SavedNotificationStore(PersistenceStore store) {
    this.store = store;
    this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  public void saveNotification(SavedNotification notification) throws IOException {
    if (notification == null || notification.getHash() == null || notification.getHash().isBlank()) {
      throw new IllegalArgumentException("Notification hash is required");
    }
    store.save(notificationKey(notification), mapper.writeValueAsBytes(notification));
  }

  public List<SavedNotification> getNotifications() throws IOException {
    return store.list(NOTIFICATION_PREFIX).stream()
        .map(key -> {
          try {
            return mapper.readValue(store.read(key).orElseThrow(), SavedNotification.class);
          } catch (IOException e) {
            throw new PersistenceReadException(e);
          }
        })
        .toList();
  }

  public void saveEvidence(SavedEvidence evidence) throws IOException {
    if (evidence == null
        || evidence.getNotificationHash() == null
        || evidence.getNotificationHash().isBlank()
        || evidence.getTimestamp() == null) {
      throw new IllegalArgumentException("Evidence notification hash and timestamp are required");
    }

    String key =
        EVIDENCE_PREFIX
            + safeId(evidence.getNamespace()) + "_"
            + safeId(evidence.getPod()) + "_"
            + evidence.getPid() + "/"
            + evidence.getTimestamp().toEpochMilli() + "_"
            + safeId(evidence.getNotificationHash()) + ".json";

    store.save(key, mapper.writeValueAsBytes(evidence));
  }

  public List<SavedEvidence> getEvidence(
      String namespace, String pod, long pid, String notificationHash) throws IOException {
    String prefix =
        EVIDENCE_PREFIX + safeId(namespace) + "_" + safeId(pod) + "_" + pid + "/";

    return store.list(prefix).stream()
        .filter(key -> key.endsWith(".json"))
        .map(key -> {
          try {
            return mapper.readValue(store.read(key).orElseThrow(), SavedEvidence.class);
          } catch (IOException e) {
            throw new PersistenceReadException(e);
          }
        })
        .filter(evidence -> notificationHash.equals(evidence.getNotificationHash()))
        .toList();
  }

  public void deleteExpired(Instant cutoff) throws IOException {
    for (String key : store.list(NOTIFICATION_PREFIX)) {
      OptionalRecord record = readNotification(key);
      if (record != null && !record.latest().isAfter(cutoff)) {
        store.delete(key);
      }
    }

    for (String key : store.list(EVIDENCE_PREFIX)) {
      SavedEvidence evidence =
          mapper.readValue(store.read(key).orElseThrow(), SavedEvidence.class);
      if (evidence.getTimestamp() != null && !evidence.getTimestamp().isAfter(cutoff)) {
        store.delete(key);
      }
    }
  }

  private OptionalRecord readNotification(String key) throws IOException {
    byte[] data = store.read(key).orElse(null);
    if (data == null) return null;

    SavedNotification notification = mapper.readValue(data, SavedNotification.class);
    Instant latest =
        notification.getInstances() == null || notification.getInstances().isEmpty()
            ? Instant.EPOCH
            : notification.getInstances().stream().max(Instant::compareTo).orElse(Instant.EPOCH);
    return new OptionalRecord(latest);
  }

  private record OptionalRecord(Instant latest) {}

  private String notificationKey(SavedNotification notification) {
    return NOTIFICATION_PREFIX
        + safeId(notification.getNamespace()) + "_"
        + safeId(notification.getPod()) + "_"
        + notification.getPid() + "_"
        + safeId(notification.getHash()) + ".json";
  }

  private String safeId(String id) {
    return id == null ? "unknown" : id.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static final class PersistenceReadException extends RuntimeException {
    private PersistenceReadException(IOException cause) {
      super(cause);
    }
  }
}
