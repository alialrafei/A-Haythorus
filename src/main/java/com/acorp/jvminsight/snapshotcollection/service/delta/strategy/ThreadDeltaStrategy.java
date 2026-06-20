package com.acorp.jvminsight.snapshotcollection.service.delta.strategy;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaComputationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes thread-related deltas between two snapshots.
 *
 * <p>Calculates:
 *
 * <ul>
 *   <li>Thread count growth
 *   <li>Deadlock count growth
 * </ul>
 *
 * Positive values indicate increasing thread activity.
 */
public final class ThreadDeltaStrategy implements DeltaComputationStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(ThreadDeltaStrategy.class);

  @Override
  public void compute(JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta) {

    long threadDelta = current.getThreadCount() - previous.getThreadCount();

    delta.setThreadDelta(threadDelta);

    int previousDeadlocks = previous.getDeadlocks() == null ? 0 : previous.getDeadlocks().length;

    int currentDeadlocks = current.getDeadlocks() == null ? 0 : current.getDeadlocks().length;

    int deadlockDelta = currentDeadlocks - previousDeadlocks;

    delta.setDeadlockDelta(deadlockDelta);

    LOGGER.trace("Computed thread delta={} deadlock delta={}", threadDelta, deadlockDelta);

    if (deadlockDelta > 0) {

      LOGGER.warn("Deadlock count increased from {} to {}", previousDeadlocks, currentDeadlocks);
    }
  }
}
