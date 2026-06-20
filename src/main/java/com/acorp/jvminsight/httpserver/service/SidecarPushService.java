package com.acorp.jvminsight.httpserver.service;

import com.acorp.jvminsight.container.PodInfoProvider;
import com.acorp.jvminsight.container.dto.AggregatorSnapshot;
import com.acorp.jvminsight.container.dto.PodInfo;
import com.acorp.jvminsight.snapshotcollection.JvmDataStore;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SidecarPushService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SidecarPushService.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final PodInfo POD_INFO = PodInfoProvider.getPodInfo();

  private SidecarPushService() {}

  public static String buildSnapshotJson() {

    try {

      LOGGER.debug("Building sidecar snapshot payload.");

      AggregatorSnapshot aggregatorSnapshot = new AggregatorSnapshot();

      List<JvmSnapshot> snapshots = new ArrayList<>();

      JvmDataStore.getDateStored().forEach((pid, snapshot) -> snapshots.add(snapshot));

      LOGGER.info(
          "Collected {} JVM snapshot(s) for pod '{}'.", snapshots.size(), POD_INFO.getName());

      aggregatorSnapshot.setJvmSnapshots(snapshots);

      aggregatorSnapshot.setTime(System.currentTimeMillis());

      aggregatorSnapshot.setPod(POD_INFO);

      String json = MAPPER.writeValueAsString(aggregatorSnapshot);

      LOGGER.debug(
          "Successfully serialized aggregator snapshot. Payload size={} bytes.", json.length());

      return json;

    } catch (Exception ex) {

      LOGGER.error("Failed to build aggregator snapshot for pod '{}'.", POD_INFO.getName(), ex);

      return null;
    }
  }
}
