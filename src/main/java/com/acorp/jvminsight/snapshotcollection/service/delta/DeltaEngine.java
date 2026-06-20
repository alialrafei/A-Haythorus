package com.acorp.jvminsight.snapshotcollection.service.delta;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.CpuDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.GcDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.HistogramDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.LeakDetectionStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.MemoryDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.MemoryPoolDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.ThreadDeltaStrategy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main orchestrator responsible for computing JVM delta snapshots.
 *
 * <p>The engine delegates individual computations to registered strategies.
 *
 * <p>Strategies are executed sequentially and independently. Failure in one strategy does not
 * prevent the remaining computations from executing.
 */
public final class DeltaEngine {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeltaEngine.class);

  /** Ordered list of delta computation strategies. */
  private static final List<DeltaComputationStrategy> STRATEGIES =
      List.of(
          new MemoryDeltaStrategy(),
          new ThreadDeltaStrategy(),
          new GcDeltaStrategy(),
          new MemoryPoolDeltaStrategy(),
          new HistogramDeltaStrategy(),
          new CpuDeltaStrategy(),
          new LeakDetectionStrategy());

  private DeltaEngine() {}

  /**
   * Computes the delta between two snapshots.
   *
   * @param previous previous snapshot
   * @param current current snapshot
   * @return computed delta snapshot
   */
  public static JvmDeltaSnapshot compute(JvmSnapshot previous, JvmSnapshot current) {
    LOGGER.debug("Starting delta computation for pid={}", current.getPid());
    JvmDeltaSnapshot delta = new JvmDeltaSnapshot();
    if (previous == null) {
      LOGGER.debug(
          "No previous snapshot found for pid={}. Returning empty delta.", current.getPid());

      return delta;
    }
    delta.setIntervalMillis(current.getTimestamp() - previous.getTimestamp());

    for (DeltaComputationStrategy strategy : STRATEGIES) {
      long start = System.nanoTime();
      try {
        strategy.compute(previous, current, delta);
        LOGGER.debug(
            "{} completed in {} μs",
            strategy.getClass().getSimpleName(),
            (System.nanoTime() - start) / 1_000);

      } catch (Exception ex) {
        LOGGER.error(
            "Strategy {} failed while computing pid={}",
            strategy.getClass().getSimpleName(),
            current.getPid(),
            ex);
      }
    }
    LOGGER.debug("Finished delta computation for pid={}", current.getPid());

    return delta;
  }
}
