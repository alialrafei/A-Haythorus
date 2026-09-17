package com.acorp.jvminsight.snapshotcollection.service.analysis;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.snapshotcollection.dto.ProcessHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.analysis.ProcessAnalysisSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime-neutral analysis orchestrator.
 *
 * <p>It operates only on process telemetry and does not depend on JVM concepts. JVM, Python, Node,
 * native, or future runtime adapters can all feed this layer once they provide
 * {@link ProcessHistorySample}.
 */
public final class RuntimeAnalysisEngine {

  private static final Logger log = LoggerFactory.getLogger(RuntimeAnalysisEngine.class);

  private static final long WINDOW_SECONDS =
      Math.max(10L, ConfigLoader.getLong("analysis.window.seconds", 60L));

  private RuntimeAnalysisEngine() {}

  public static ProcessAnalysisSnapshot analyze(List<ProcessHistorySample> history) {

    log.debug(
        "Runtime analysis started: historySize={}, windowSeconds={}",
        history.size(),
        WINDOW_SECONDS);

    List<ProcessHistorySample> window = recentWindow(history);

    log.debug(
        "Runtime analysis window: inputSamples={}, windowSamples={}",
        history.size(),
        window.size());

    if (!window.isEmpty()) {
      ProcessHistorySample first = window.get(0);
      ProcessHistorySample last = window.get(window.size() - 1);

      log.debug(
          "Runtime analysis window range: firstTimestamp={}, lastTimestamp={}, "
              + "firstCpuTimeNanos={}, lastCpuTimeNanos={}, processors={}",
          first.timestamp(),
          last.timestamp(),
          first.cpuTimeNanos(),
          last.cpuTimeNanos(),
          last.availableProcessors());
    } else {
      log.warn(
          "Runtime analysis window is EMPTY: historySize={}, windowSeconds={}",
          history.size(),
          WINDOW_SECONDS);
    }

    ProcessAnalysisSnapshot result =
        new ProcessAnalysisSnapshot(
            CpuAnalyzer.analyze(window),
            IoAnalyzer.analyze(window));

    log.debug(
        "Runtime analysis completed: cpuScore={}, ioScore={}",
        result.cpu().score(),
        result.io().score());

    return result;
  }

  private static List<ProcessHistorySample> recentWindow(
      List<ProcessHistorySample> history) {

    if (history.isEmpty()) {
      log.warn("Cannot build analysis window: history is empty");
      return List.of();
    }

    ProcessHistorySample latest = history.get(history.size() - 1);
    Instant latestTimestamp = latest.timestamp();

    log.debug(
        "Building analysis window: historySize={}, latestTimestamp={}, "
            + "latestCpuTimeNanos={}, latestProcessors={}",
        history.size(),
        latestTimestamp,
        latest.cpuTimeNanos(),
        latest.availableProcessors());

    if (latestTimestamp == null) {
      log.warn(
          "Latest history sample has null timestamp; using entire history: size={}",
          history.size());
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

    log.debug(
        "Analysis window filtering: cutoff={}, latest={}, inputSamples={}, selectedSamples={}",
        cutoff,
        latestTimestamp,
        history.size(),
        result.size());

    if (result.size() < 2) {
      log.warn(
          "Insufficient samples for historical analysis: selectedSamples={}, "
              + "inputSamples={}, windowSeconds={}, cutoff={}, latestTimestamp={}",
          result.size(),
          history.size(),
          WINDOW_SECONDS,
          cutoff,
          latestTimestamp);
    }

    return result;
  }
}