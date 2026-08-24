package com.acorp.jvminsight.snapshotcollection.service.delta.strategy;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaComputationStrategy;
import com.acorp.jvminsight.util.GrowthCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Computes thread and deadlock movement between two snapshots. */
public final class ThreadDeltaStrategy implements DeltaComputationStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(ThreadDeltaStrategy.class);

  @Override
  public void compute(JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta) {

    long previousThreads = previous.getThreadCount();
    long currentThreads = current.getThreadCount();

    delta.setPreviousThreadCount(previousThreads);
    delta.setCurrentThreadCount(currentThreads);
    delta.setThreadDelta(currentThreads - previousThreads);
    delta.setThreadGrowthPercentage(
        GrowthCalculator.percentageGrowth(previousThreads, currentThreads));

    int previousDeadlocks = previous.getDeadlocks() == null ? 0 : previous.getDeadlocks().length;
    int currentDeadlocks = current.getDeadlocks() == null ? 0 : current.getDeadlocks().length;

    delta.setCurrentDeadlockCount(currentDeadlocks);
    delta.setDeadlockDelta(currentDeadlocks - previousDeadlocks);

    LOGGER.trace(
        "Computed thread delta={} deadlock delta={}",
        delta.getThreadDelta(),
        delta.getDeadlockDelta());

    if (delta.getDeadlockDelta() > 0) {
      LOGGER.warn("Deadlock count increased from {} to {}", previousDeadlocks, currentDeadlocks);
    }
  }
}
