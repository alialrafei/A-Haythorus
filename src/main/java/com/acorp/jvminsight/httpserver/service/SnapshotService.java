package com.acorp.jvminsight.httpserver.service;

import com.acorp.jvminsight.container.PodInfoProvider;
import com.acorp.jvminsight.container.dto.AggregatorSnapshot;
import com.acorp.jvminsight.container.dto.PodInfo;
import com.acorp.jvminsight.snapshotcollection.JvmDataStore;
import com.acorp.jvminsight.snapshotcollection.dto.JvmHistoryResponse;
import com.acorp.jvminsight.snapshotcollection.dto.JvmHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SnapshotService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final PodInfo POD_INFO = PodInfoProvider.getPodInfo();

  private SnapshotService() {}

  public static String buildSnapshotJson() {
    try {
      return MAPPER.writeValueAsString(getSnapshot());
    } catch (Exception ex) {
      LOGGER.error("Failed to serialize snapshot.", ex);
      return null;
    }
  }

  public static AggregatorSnapshot getSnapshot() {
    AggregatorSnapshot snapshot = new AggregatorSnapshot();
    List<JvmSnapshot> snapshots = new ArrayList<>();

    JvmDataStore.getDateStored().forEach((pid, jvm) -> snapshots.add(jvm));

    snapshot.setJvmSnapshots(snapshots);
    snapshot.setTime(Instant.now());
    snapshot.setPod(POD_INFO);

    return snapshot;
  }

  public static List<JvmHistorySample> getJvmHistory(long pid) {
    return JvmDataStore.getHistory(pid);
  }

  public static List<JvmHistoryResponse> getLocalJvmHistories() {
    return getJvmSnapshots().stream()
        .map(
            jvm ->
                new JvmHistoryResponse(
                    POD_INFO,
                    jvm.getPid(),
                    Instant.now(),
                    JvmDataStore.getHistory(jvm.getPid())))
        .toList();
  }

  public static List<JvmSnapshot> getJvmSnapshots() {
    return getSnapshot().getJvmSnapshots();
  }

  public static Optional<JvmSnapshot> getJvmSnapshot(long pid) {
    return getJvmSnapshots().stream().filter(snapshot -> snapshot.getPid() == pid).findFirst();
  }
}
