package com.acorp.jvminsight.snapshotcollection.service.delta.strategy;

import com.acorp.jvminsight.memory.GcSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.GcDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaComputationStrategy;
import com.acorp.jvminsight.util.GrowthCalculator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes garbage collection activity between two snapshots.
 *
 * <p>Matching is performed using GC name.
 *
 * <p>Computes: - previous values - current values - deltas - growth percentages
 *
 * <p>This implementation is stateless and thread-safe.
 */
public final class GcDeltaStrategy implements DeltaComputationStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(GcDeltaStrategy.class);

  @Override
  public void compute(JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta) {

    if (previous.getGc() == null || current.getGc() == null) {

      LOGGER.debug("Skipping GC delta computation due to missing snapshots.");

      return;
    }

    Map<String, GcSnapshot> previousMap = toMap(previous.getGc());

    List<GcDeltaSnapshot> gcDeltas = new ArrayList<>();

    for (GcSnapshot currentGc : current.getGc()) {

      GcSnapshot previousGc = previousMap.get(currentGc.name);

      if (previousGc == null) {
        continue;
      }

      GcDeltaSnapshot gcDelta = new GcDeltaSnapshot();

      gcDelta.setGcName(currentGc.name);

      // Collection Count

      gcDelta.setPreviousCollectionCount(previousGc.collectionCount);

      gcDelta.setCurrentCollectionCount(currentGc.collectionCount);

      gcDelta.setCollectionCountDelta(currentGc.collectionCount - previousGc.collectionCount);

      gcDelta.setCollectionCountGrowthPercentage(
          GrowthCalculator.percentageGrowth(previousGc.collectionCount, currentGc.collectionCount));

      // Collection Time

      gcDelta.setPreviousCollectionTimeMillis(previousGc.collectionTimeMillis);

      gcDelta.setCurrentCollectionTimeMillis(currentGc.collectionTimeMillis);

      gcDelta.setCollectionTimeDeltaMillis(
          currentGc.collectionTimeMillis - previousGc.collectionTimeMillis);

      gcDelta.setCollectionTimeGrowthPercentage(
          GrowthCalculator.percentageGrowth(
              previousGc.collectionTimeMillis, currentGc.collectionTimeMillis));

      gcDeltas.add(gcDelta);

      LOGGER.trace(
          "GC={} countGrowth={}%, timeGrowth={}%",
          currentGc.name,
          String.format("%.2f", gcDelta.getCollectionCountGrowthPercentage()),
          String.format("%.2f", gcDelta.getCollectionTimeGrowthPercentage()));
    }

    delta.setGcDelta(gcDeltas);

    LOGGER.debug("Computed {} GC deltas.", gcDeltas.size());
  }

  private Map<String, GcSnapshot> toMap(List<GcSnapshot> snapshots) {

    Map<String, GcSnapshot> map = new HashMap<>(snapshots.size());

    for (GcSnapshot snapshot : snapshots) {

      map.put(snapshot.name, snapshot);
    }

    return map;
  }
}
