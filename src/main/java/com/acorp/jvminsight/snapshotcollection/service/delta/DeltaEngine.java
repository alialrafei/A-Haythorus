package com.acorp.jvminsight.snapshotcollection.service.delta;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.CpuDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.GcDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.HistogramDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.MemoryDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.MemoryPoolDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.ThreadDeltaStrategy;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main orchestrator for JVM delta and trend computation.
 *
 * <p>Pairwise strategies answer "what changed since the previous sample?". Historical leak analysis
 * runs afterwards and answers "has suspicious retention persisted across the analysis window?".
 */
public final class DeltaEngine {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeltaEngine.class);

  private static final List<DeltaComputationStrategy> PAIRWISE_STRATEGIES =
      List.of(
          new MemoryDeltaStrategy(),
          new ThreadDeltaStrategy(),
          new GcDeltaStrategy(),
          new MemoryPoolDeltaStrategy(),
          new HistogramDeltaStrategy(),
          new CpuDeltaStrategy());

  private DeltaEngine() {}

  /** Compatibility overload for callers that only have two snapshots. */
  public static JvmDeltaSnapshot compute(JvmSnapshot previous, JvmSnapshot current) {
    List<JvmSnapshot> history = previous == null ? List.of() : List.of(previous);
    return compute(history, current);
  }

  /**
   * Computes the latest pairwise movement and the history-aware leak confidence.
   *
   * @param retainedHistory snapshots already stored for this PID, oldest to newest
   * @param current newly collected snapshot, not yet inserted in the store
   */
  public static JvmDeltaSnapshot compute(List<JvmSnapshot> retainedHistory, JvmSnapshot current) {
    LOGGER.debug("Starting delta computation for pid={}", current.getPid());

    JvmDeltaSnapshot delta = new JvmDeltaSnapshot();
    JvmSnapshot previous =
        retainedHistory.isEmpty() ? null : retainedHistory.get(retainedHistory.size() - 1);

    if (previous != null) {
      if (previous.getTimestamp() != null && current.getTimestamp() != null) {
        delta.setIntervalMillis(Duration.between(previous.getTimestamp(), current.getTimestamp()));
      }

      for (DeltaComputationStrategy strategy : PAIRWISE_STRATEGIES) {
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
    } else {
      LOGGER.debug("No previous snapshot found for pid={}; pairwise delta is empty.", current.getPid());
    }

    try {
      HistoricalLeakAnalyzer.analyze(retainedHistory, current, delta);
    } catch (Exception ex) {
      LOGGER.error("Historical leak analysis failed for pid={}", current.getPid(), ex);
    }

    LOGGER.debug("Finished delta computation for pid={}", current.getPid());
    return delta;
  }
}
