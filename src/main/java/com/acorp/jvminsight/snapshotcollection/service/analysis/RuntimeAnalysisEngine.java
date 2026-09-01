package com.acorp.jvminsight.snapshotcollection.service.analysis;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.snapshotcollection.dto.ProcessHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.analysis.ProcessAnalysisSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime-neutral analysis orchestrator.
 *
 * <p>It operates only on process telemetry and does not depend on JVM concepts. JVM, Python, Node,
 * native, or future runtime adapters can all feed this layer once they provide
 * {@link ProcessHistorySample}.
 */
public final class RuntimeAnalysisEngine {

  private static final long WINDOW_SECONDS =
      Math.max(10L, ConfigLoader.getLong("analysis.window.seconds", 60L));

  private RuntimeAnalysisEngine() {}

  public static ProcessAnalysisSnapshot analyze(List<ProcessHistorySample> history) {
    List<ProcessHistorySample> window = recentWindow(history);

    return new ProcessAnalysisSnapshot(
        CpuAnalyzer.analyze(window),
        IoAnalyzer.analyze(window));
  }

  private static List<ProcessHistorySample> recentWindow(List<ProcessHistorySample> history) {
    if (history.isEmpty()) {
      return List.of();
    }

    ProcessHistorySample latest = history.get(history.size() - 1);
    Instant latestTimestamp = latest.timestamp();

    if (latestTimestamp == null) {
      return List.copyOf(history);
    }

    Instant cutoff = latestTimestamp.minusSeconds(WINDOW_SECONDS);
    List<ProcessHistorySample> result = new ArrayList<>();

    for (ProcessHistorySample sample : history) {
      Instant timestamp = sample.timestamp();
      if (timestamp != null && !timestamp.isBefore(cutoff)) {
        result.add(sample);
      }
    }

    return result;
  }
}
