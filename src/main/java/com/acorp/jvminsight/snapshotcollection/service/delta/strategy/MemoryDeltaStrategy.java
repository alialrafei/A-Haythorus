package com.acorp.jvminsight.snapshotcollection.service.delta.strategy;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaComputationStrategy;
import com.acorp.jvminsight.util.GrowthCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes heap and non-heap memory growth between two JVM snapshots.
 *
 * <p>This strategy calculates:
 *
 * <ul>
 *   <li>Heap delta
 *   <li>Heap growth percentage
 *   <li>Non-heap delta
 *   <li>Non-heap growth percentage
 * </ul>
 *
 * Positive values indicate memory growth while negative values indicate memory reclamation.
 *
 * <p>This implementation is stateless and thread-safe.
 */
public final class MemoryDeltaStrategy implements DeltaComputationStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(MemoryDeltaStrategy.class);

  @Override
  public void compute(JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta) {

    if (previous.getMemory() == null || current.getMemory() == null) {

      LOGGER.debug("Skipping memory delta computation due to missing memory snapshots.");

      return;
    }

    long previousHeap = previous.getMemory().heapUsed;

    long currentHeap = current.getMemory().heapUsed;

    long heapDelta = currentHeap - previousHeap;

    long previousNonHeap = previous.getMemory().nonHeapUsed;

    long currentNonHeap = current.getMemory().nonHeapUsed;

    long nonHeapDelta = currentNonHeap - previousNonHeap;

    delta.setPreviousHeapUsed(previousHeap);

    delta.setCurrentHeapUsed(currentHeap);

    delta.setHeapDelta(heapDelta);

    delta.setHeapGrowthPercentage(GrowthCalculator.percentageGrowth(previousHeap, currentHeap));

    delta.setPreviousNonHeapUsed(previousNonHeap);

    delta.setCurrentNonHeapUsed(currentNonHeap);

    delta.setNonHeapDelta(nonHeapDelta);

    delta.setNonHeapGrowthPercentage(
        GrowthCalculator.percentageGrowth(previousNonHeap, currentNonHeap));

    LOGGER.debug(
        "Computed memory delta: heap={} bytes ({:.2f}%), nonHeap={} bytes ({:.2f}%)",
        heapDelta,
        delta.getHeapGrowthPercentage(),
        nonHeapDelta,
        delta.getNonHeapGrowthPercentage());
  }
}
