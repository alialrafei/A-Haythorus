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
    if (notification == null || notification.getId() == null || notification.getId().isBlank()) {
      throw new IllegalArgumentException("Notification id/code is required");
    }

    store.save(
        notificationKey(notification),
        mapper.writeValueAsBytes(notification));
  }

  public List<SavedNotification> getNotifications() throws IOException {
    return store.list(NOTIFICATION_PREFIX).stream()
        .map(
            key -> {
              try {
                return mapper.readValue(store.read(key).orElseThrow(), SavedNotification.class);
              } catch (IOException e) {
                throw new PersistenceReadException(e);
              }
            })
        .toList();
  }

  public Optional<SavedNotification> getNotification(String id) throws IOException {
    if (id == null || id.isBlank()) {
      return Optional.empty();
    }

    Optional<byte[]> data = store.list(NOTIFICATION_PREFIX).stream()
        .filter(key -> key.endsWith("_" + safeId(id) + ".json"))
        .findFirst()
        .flatMap(key -> {
          try {
            return store.read(key);
          } catch (IOException e) {
            throw new PersistenceReadException(e);
          }
        });

    if (data.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(mapper.readValue(data.get(), SavedNotification.class));
  }

  public void recordEvidence(
      String namespace,
      String pod,
      long pid,
      String notificationId,
      String message,
      Instant timestamp,
      Object payload) throws IOException {
    SavedEvidence evidence =
        new SavedEvidence(
            notificationId,
            namespace,
            pod,
            pid,
            timestamp,
            message,
            payload == null ? null : mapper.valueToTree(payload));
    saveEvidence(evidence);
  }

  public void saveEvidence(SavedEvidence evidence) throws IOException {
    if (evidence == null
        || evidence.getNotificationId() == null
        || evidence.getNotificationId().isBlank()
        || evidence.getTimestamp() == null) {
      throw new IllegalArgumentException("Evidence notification id and timestamp are required");
    }

    String key =
        EVIDENCE_PREFIX
            + safeId(evidence.getNamespace())
            + "_"
            + safeId(evidence.getPod())
            + "_"
            + evidence.getPid()
            + "/"
            + evidence.getTimestamp().toEpochMilli()
            + ".json";

    store.save(key, mapper.writeValueAsBytes(evidence));
  }

  public List<SavedEvidence> getEvidence(
      String namespace, String pod, long pid, String notificationId) throws IOException {
    String prefix =
        EVIDENCE_PREFIX
            + safeId(namespace)
            + "_"
            + safeId(pod)
            + "_"
            + pid
            + "/";
    return store.list(prefix).stream()
        .filter(key -> key.endsWith(".json"))
        .map(
            key -> {
              try {
                return mapper.readValue(store.read(key).orElseThrow(), SavedEvidence.class);
              } catch (IOException e) {
                throw new PersistenceReadException(e);
              }
            })
        .filter(evidence -> notificationId.equals(evidence.getNotificationId()))
        .toList();
  }

  private String notificationKey(SavedNotification notification) {
    return NOTIFICATION_PREFIX
        + safeId(notification.getNamespace())
        + "_"
        + safeId(notification.getPod())
        + "_"
        + notification.getPid()
        + "_"
        + safeId(notification.getId())
        + ".json";
  }

  private String safeId(String id) {
    return id.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static final class PersistenceReadException extends RuntimeException {
    private PersistenceReadException(IOException cause) {
      super(cause);
    }
  }
}
