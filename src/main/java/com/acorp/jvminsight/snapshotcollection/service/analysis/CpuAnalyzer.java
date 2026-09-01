package com.acorp.jvminsight.snapshotcollection.service.analysis;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.snapshotcollection.dto.ProcessHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.analysis.AnalysisResult;
import com.acorp.jvminsight.snapshotcollection.dto.analysis.EvidenceSignal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runtime-neutral historical CPU analyzer.
 *
 * <p>For each valid interval:
 *
 * <pre>
 * U_i = deltaCpuTime / (deltaWallTime * processors)
 * </pre>
 *
 * where {@code U_i} is clamped to [0,1].
 *
 * <p>The analyzer then computes:
 *
 * <pre>
 * meanUtilization = mean(U_i)
 * peakUtilization = max(U_i)
 * persistence     = peak == 0 ? 0 : mean / peak
 * sustainedPressure = meanUtilization * persistence
 * </pre>
 *
 * <p>{@code persistence} is naturally normalized: a short spike has mean much smaller than peak,
 * while sustained load keeps mean close to peak. The final score therefore describes sustained CPU
 * pressure, not whether high CPU is inherently "bad".
 */
public final class CpuAnalyzer {

  private static final double UTILIZATION_WEIGHT =
      WeightedEvidenceScore.requireNonNegativeWeight(
          "analysis.cpu.utilization.weight",
          ConfigLoader.getDouble("analysis.cpu.utilization.weight", 1.0));

  private static final double PERSISTENCE_WEIGHT =
      WeightedEvidenceScore.requireNonNegativeWeight(
          "analysis.cpu.persistence.weight",
          ConfigLoader.getDouble("analysis.cpu.persistence.weight", 1.0));

  private CpuAnalyzer() {}

  public static AnalysisResult analyze(List<ProcessHistorySample> samples) {
    List<Double> utilizations = intervalUtilizations(samples);

    if (utilizations.isEmpty()) {
      return unavailable();
    }

    double mean = utilizations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    double peak = utilizations.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    double persistence = peak <= 0.0 ? 0.0 : clamp01(mean / peak);
    List<EvidenceSignal> evidence =
        List.of(
            EvidenceSignal.available(
                "cpu-utilization",
                mean,
                "Average process CPU utilization normalized by available processors."),
            EvidenceSignal.available(
                "cpu-persistence",
                persistence,
                "How close average utilization stayed to the observed peak."));

    double sustainedPressure =
        WeightedEvidenceScore.calculate(
            WeightedEvidenceScore.weighted(evidence.get(0), UTILIZATION_WEIGHT),
            WeightedEvidenceScore.weighted(evidence.get(1), PERSISTENCE_WEIGHT));

    Map<String, Double> metrics =
        Map.of(
            "averageUtilizationPercent", mean * 100.0,
            "peakUtilizationPercent", peak * 100.0,
            "persistencePercent", persistence * 100.0,
            "intervalsAnalyzed", (double) utilizations.size());

    List<String> reasons =
        List.of(
            String.format(
                "CPU averaged %.1f%% of available processor capacity, peaked at %.1f%%, and had %.0f%% persistence.",
                mean * 100.0, peak * 100.0, persistence * 100.0));

    return new AnalysisResult(
        "cpu",
        "Sustained CPU pressure",
        sustainedPressure * 100.0,
        evidence,
        metrics,
        reasons);
  }

  private static List<Double> intervalUtilizations(List<ProcessHistorySample> samples) {
    List<Double> result = new ArrayList<>();

    for (int i = 1; i < samples.size(); i++) {
      ProcessHistorySample previous = samples.get(i - 1);
      ProcessHistorySample current = samples.get(i);

      if (previous.timestamp() == null
          || current.timestamp() == null
          || !current.timestamp().isAfter(previous.timestamp())
          || current.availableProcessors() <= 0) {
        continue;
      }

      long cpuDelta = current.cpuTimeNanos() - previous.cpuTimeNanos();
      if (cpuDelta < 0) {
        continue;
      }

      long wallNanos = Duration.between(previous.timestamp(), current.timestamp()).toNanos();
      if (wallNanos <= 0) {
        continue;
      }

      double capacityNanos = (double) wallNanos * current.availableProcessors();
      result.add(clamp01(cpuDelta / capacityNanos));
    }

    return result;
  }

  private static AnalysisResult unavailable() {
    return new AnalysisResult(
        "cpu",
        "Sustained CPU pressure",
        0.0,
        List.of(
            EvidenceSignal.unavailable(
                "cpu-utilization", "Insufficient process CPU history."),
            EvidenceSignal.unavailable(
                "cpu-persistence", "Insufficient process CPU history.")),
        Map.of(),
        List.of("Insufficient process CPU history to evaluate sustained pressure."));
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
