package com.acorp.jvminsight.snapshotcollection.service.delta;

import com.acorp.jvminsight.runtime.jvm.JvmProcessHistoryAdapter;
import com.acorp.jvminsight.snapshotcollection.dto.JvmHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.ProcessHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.analysis.ProcessAnalysisSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.analysis.RuntimeAnalysisEngine;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.CpuDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.GcDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.HistogramDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.IoDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.MemoryDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.MemoryPoolDeltaStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.strategy.ThreadDeltaStrategy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main orchestrator for pairwise JVM deltas plus historical analysis.
 *
 * <p>Pairwise strategies still describe the newest interval. Runtime-neutral CPU/I/O analysis is
 * delegated to {@link RuntimeAnalysisEngine}; JVM-only retention analysis remains in
 * {@link HistoricalLeakAnalyzer}.
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
          new CpuDeltaStrategy(),
          new IoDeltaStrategy());

  private DeltaEngine() {}

  public static JvmDeltaSnapshot compute(JvmSnapshot previous, JvmSnapshot current) {
    List<JvmHistorySample> history =
        previous == null ? List.of() : List.of(JvmHistorySample.from(previous));
    return compute(history, previous, current);
  }

  public static JvmDeltaSnapshot compute(
      List<JvmHistorySample> retainedHistory, JvmSnapshot previous, JvmSnapshot current) {

    LOGGER.debug("Starting delta computation for pid={}", current.getPid());
    JvmDeltaSnapshot delta = new JvmDeltaSnapshot();

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
    }

    try {
      HistoricalLeakAnalyzer.analyze(retainedHistory, current, delta);
    } catch (Exception ex) {
      LOGGER.error("Historical leak analysis failed for pid={}", current.getPid(), ex);
    }

    try {
      List<ProcessHistorySample> processHistory = new ArrayList<>(retainedHistory.size() + 1);
      retainedHistory.stream().map(JvmHistorySample::process).forEach(processHistory::add);
      processHistory.add(JvmProcessHistoryAdapter.from(current));

      ProcessAnalysisSnapshot processAnalysis = RuntimeAnalysisEngine.analyze(processHistory);
      delta.setCpuAnalysis(processAnalysis.cpu());
      delta.setIoAnalysis(processAnalysis.io());
    } catch (Exception ex) {
      LOGGER.error("Runtime-neutral process analysis failed for pid={}", current.getPid(), ex);
    }

    LOGGER.debug("Finished delta computation for pid={}", current.getPid());
    return delta;
  }
}
