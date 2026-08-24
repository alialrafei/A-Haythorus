package com.acorp.jvminsight.snapshotcollection.service.delta.strategy;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaComputationStrategy;
import com.acorp.jvminsight.util.GrowthCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes heap and non-heap memory movement between two JVM snapshots.
 *
 * <p>The signed delta is preserved, but growth and reclamation are also separated explicitly.
 * This matters for leak analysis: a positive delta is retention/growth evidence while a negative
 * delta is evidence that memory was reclaimed.
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
    delta.setPositiveHeapDelta(Math.max(heapDelta, 0));
    delta.setReclaimedHeapBytes(Math.max(-heapDelta, 0));
    delta.setHeapGrowthPercentage(GrowthCalculator.percentageGrowth(previousHeap, currentHeap));

    delta.setPreviousNonHeapUsed(previousNonHeap);
    delta.setCurrentNonHeapUsed(currentNonHeap);
    delta.setNonHeapDelta(nonHeapDelta);
    delta.setPositiveNonHeapDelta(Math.max(nonHeapDelta, 0));
    delta.setReclaimedNonHeapBytes(Math.max(-nonHeapDelta, 0));
    delta.setNonHeapGrowthPercentage(
        GrowthCalculator.percentageGrowth(previousNonHeap, currentNonHeap));

    LOGGER.debug(
        "Computed memory delta: heap={} bytes, growth={} bytes, reclaimed={} bytes; "
            + "nonHeap={} bytes, growth={} bytes, reclaimed={} bytes",
        heapDelta,
        delta.getPositiveHeapDelta(),
        delta.getReclaimedHeapBytes(),
        nonHeapDelta,
        delta.getPositiveNonHeapDelta(),
        delta.getReclaimedNonHeapBytes());
  }
}
