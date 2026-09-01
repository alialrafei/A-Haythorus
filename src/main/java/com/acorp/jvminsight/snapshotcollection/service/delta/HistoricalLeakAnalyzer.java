package com.acorp.jvminsight.snapshotcollection.service.delta;

import static com.acorp.jvminsight.util.GrowthCalculator.bytesToMb;
import static com.acorp.jvminsight.util.GrowthCalculator.clamp01;
import static com.acorp.jvminsight.util.GrowthCalculator.clamp100;
import static com.acorp.jvminsight.util.GrowthCalculator.validatedAlpha;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.snapshotcollection.dto.JvmHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.analysis.EvidenceSignal;
import com.acorp.jvminsight.snapshotcollection.dto.delta.HistogramDelta;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.LeakSeverity;
import com.acorp.jvminsight.snapshotcollection.dto.delta.Recommendation;
import com.acorp.jvminsight.snapshotcollection.dto.delta.RecommendationSeverity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * History-aware JVM memory-retention analyzer.
 *
 * <p>Each signal is normalized to [0,1]. Unavailable evidence is excluded from the aggregate rather
 * than counted as zero. This distinction matters when a collector/runtime cannot provide one of
 * the signals.
 */
public final class HistoricalLeakAnalyzer {

  private static final long WINDOW_SECONDS =
      Math.max(10L, ConfigLoader.getLong("leak.window.seconds", 60L));

  private static final double ALPHA =
      validatedAlpha(ConfigLoader.getDouble("leak.ewma.alpha", 0.35));

  private static final double HISTORICAL_WEIGHT = 1.0 - ALPHA;

  private static final double HEAP_RETENTION_WEIGHT =
      nonNegativeWeight(
          "analysis.memory.heap-retention.weight",
          ConfigLoader.getDouble("analysis.memory.heap-retention.weight", 1.0));

  private static final double OLD_GEN_RETENTION_WEIGHT =
      nonNegativeWeight(
          "analysis.memory.old-gen-retention.weight",
          ConfigLoader.getDouble("analysis.memory.old-gen-retention.weight", 1.0));

  private static final double GC_RECLAIM_WEIGHT =
      nonNegativeWeight(
          "analysis.memory.gc-reclaim.weight",
          ConfigLoader.getDouble("analysis.memory.gc-reclaim.weight", 1.0));

  private static final double HISTOGRAM_GROWTH_WEIGHT =
      nonNegativeWeight(
          "analysis.memory.histogram-growth.weight",
          ConfigLoader.getDouble("analysis.memory.histogram-growth.weight", 1.0));

  private HistoricalLeakAnalyzer() {}

  public static void analyze(
      List<JvmHistorySample> retainedHistory, JvmSnapshot current, JvmDeltaSnapshot delta) {

    List<JvmHistorySample> window = buildWindow(retainedHistory, JvmHistorySample.from(current));
    if (window.size() < 2) {
      initializeInsufficientHistory(delta);
      return;
    }

    List<String> reasons = new ArrayList<>();
    List<Recommendation> recommendations = new ArrayList<>();

    Trend heapTrend = trend(window.stream().map(JvmHistorySample::heapUsed).toList());
    Trend oldGenTrend =
        trend(window.stream().map(JvmHistorySample::oldGenerationUsed).filter(v -> v > 0).toList());

    long gcCollections = gcCollections(window.get(0), window.get(window.size() - 1));

    delta.setHeapGrowthPersistence(heapTrend.persistence());
    delta.setWindowHeapGrowthBytes(heapTrend.netGrowth());
    delta.setWindowGcCollections(gcCollections);

    EvidenceSignal heap = heapEvidence(heapTrend, reasons);
    EvidenceSignal oldGen = oldGenerationEvidence(oldGenTrend, reasons);
    EvidenceSignal gc = gcReclaimEvidence(heapTrend, gcCollections, reasons);
    EvidenceSignal histogram = histogramEvidence(delta, reasons);

    double normalizedEvidence =
        weightedAvailableEvidence(
            weighted(heap, HEAP_RETENTION_WEIGHT),
            weighted(oldGen, OLD_GEN_RETENTION_WEIGHT),
            weighted(gc, GC_RECLAIM_WEIGHT),
            weighted(histogram, HISTOGRAM_GROWTH_WEIGHT));
    double rawEvidenceScore = normalizedEvidence * 100.0;
    double maturity = windowMaturity(window);
    double instantaneousScore = rawEvidenceScore * maturity;

    double previousConfidence =
        retainedHistory.isEmpty()
            ? 0.0
            : retainedHistory.get(retainedHistory.size() - 1).leakConfidence();

    double confidence =
        clamp100(ALPHA * instantaneousScore + HISTORICAL_WEIGHT * previousConfidence);

    delta.setInstantaneousLeakScore(instantaneousScore);
    delta.setLeakScore(confidence);
    delta.setHistoricalWeight(HISTORICAL_WEIGHT);
    delta.setLeakSeverity(determineSeverity(confidence));
    delta.setLeakReasons(reasons);

    if (confidence >= 30.0) {
      recommendations.add(memoryRecommendation(confidence, reasons));
    }
    delta.setRecommendations(recommendations);
  }

  /*
   * =========================================================
   * EVIDENCE FORMULAS
   * =========================================================
   *
   * Every available signal returns E in [0,1].
   *
   * IMPORTANT:
   *   E = 0      => signal was observed and showed no suspicious behavior.
   *   unavailable => signal could not be evaluated and must not lower the average.
   *
   * Final weighted memory evidence:
   *
   *       sum(w_i * E_i) over available signals
   * E = -----------------------------------------
   *       sum(w_i) over available signals
   *
   * Default weights are all 1.0, so the formula reduces to the arithmetic mean.
   * Multiplying every configured weight by the same constant does not change the score.
   * =========================================================
   */

  /**
   * Heap evidence:
   *
   * <pre>
   * persistence   = positiveIntervals / totalIntervals
   * retention     = max(netGrowth, 0) / positiveGrowth
   * heapEvidence  = (persistence + retention) / 2
   * </pre>
   */
  private static EvidenceSignal heapEvidence(Trend trend, List<String> reasons) {
    if (!trend.available()) {
      return EvidenceSignal.unavailable("heap-retention", "Heap history is unavailable.");
    }

    if (trend.netGrowth() <= 0L) {
      return EvidenceSignal.available(
          "heap-retention", 0.0, "Heap did not retain positive net growth.");
    }

    double value = mean(trend.persistence(), trend.retentionRatio());

    reasons.add(
        String.format(
            "Heap retained %.2f MB; %.0f%% of intervals moved upward and %.0f%% of positive growth remained retained.",
            bytesToMb(trend.netGrowth()),
            trend.persistence() * 100.0,
            trend.retentionRatio() * 100.0));

    return EvidenceSignal.available(
        "heap-retention", value, "Persistence and retained positive heap movement.");
  }

  /**
   * Old-generation evidence:
   *
   * <pre>
   * oldGenEvidence = (oldGenPersistence + oldGenRetention) / 2
   * </pre>
   *
   * <p>If no old-generation pool can be resolved, this signal is unavailable rather than zero.
   */
  private static EvidenceSignal oldGenerationEvidence(Trend trend, List<String> reasons) {
    if (!trend.available()) {
      return EvidenceSignal.unavailable(
          "old-gen-retention", "Old-generation history is unavailable.");
    }

    if (trend.netGrowth() <= 0L) {
      return EvidenceSignal.available(
          "old-gen-retention", 0.0, "Old generation did not retain positive net growth.");
    }

    double value = mean(trend.persistence(), trend.retentionRatio());

    reasons.add(
        String.format(
            "Old-generation usage retained %.2f MB; %.0f%% of intervals moved upward and %.0f%% of positive growth remained retained.",
            bytesToMb(trend.netGrowth()),
            trend.persistence() * 100.0,
            trend.retentionRatio() * 100.0));

    return EvidenceSignal.available(
        "old-gen-retention", value, "Persistence and retained old-generation movement.");
  }

  /**
   * GC reclaim evidence:
   *
   * <pre>
   * reclaimRatio   = reclaimedBytes / positiveGrowthBytes
   * retentionRatio = 1 - reclaimRatio
   * gcEvidence     = retentionRatio * heapPersistence
   * </pre>
   *
   * <p>No GC in the window means the signal is unavailable: there was no observed reclamation
   * opportunity to judge.
   */
  private static EvidenceSignal gcReclaimEvidence(
      Trend heapTrend, long gcCollections, List<String> reasons) {

    if (!heapTrend.available()) {
      return EvidenceSignal.unavailable("gc-reclaim", "Heap history is unavailable.");
    }

    if (gcCollections <= 0L) {
      return EvidenceSignal.unavailable(
          "gc-reclaim", "No GC collection occurred in the analysis window.");
    }

    if (heapTrend.positiveGrowth() <= 0L) {
      return EvidenceSignal.available(
          "gc-reclaim", 0.0, "GC ran, but no positive heap growth needed reclaim analysis.");
    }

    double reclaimRatio =
        clamp01(heapTrend.reclaimed() / (double) heapTrend.positiveGrowth());
    double retentionRatio = 1.0 - reclaimRatio;
    double value = clamp01(retentionRatio * heapTrend.persistence());

    reasons.add(
        String.format(
            "%d GC collection(s) occurred; %.0f%% of observed positive heap growth was reclaimed and %.0f%% remained retained.",
            gcCollections, reclaimRatio * 100.0, retentionRatio * 100.0));

    return EvidenceSignal.available(
        "gc-reclaim", value, "Retention remaining after observed GC activity.");
  }

  /**
   * Histogram evidence uses full matched-class movement calculated before top-N UI truncation:
   *
   * <pre>
   * growthDominance = positiveBytes / (positiveBytes + reclaimedBytes)
   * topClassShare   = largestPositiveClassDelta / positiveBytes
   * histogramEvidence = (growthDominance + topClassShare) / 2
   * </pre>
   */
  private static EvidenceSignal histogramEvidence(
      JvmDeltaSnapshot delta, List<String> reasons) {

    if (delta.getHistogramDelta() == null) {
      return EvidenceSignal.unavailable(
          "histogram-growth", "Histogram delta is unavailable.");
    }

    long positiveBytes = delta.getHistogramPositiveBytes();
    long reclaimedBytes = delta.getHistogramReclaimedBytes();

    if (positiveBytes <= 0L) {
      return EvidenceSignal.available(
          "histogram-growth", 0.0, "No positive matched-class histogram growth was observed.");
    }

    double totalMovement = (double) positiveBytes + reclaimedBytes;
    double growthDominance = totalMovement <= 0.0 ? 0.0 : positiveBytes / totalMovement;
    double topClassShare =
        clamp01(delta.getHistogramTopClassPositiveBytes() / (double) positiveBytes);

    double value = mean(growthDominance, topClassShare);

    HistogramDelta topGrowingClass =
        delta.getHistogramDelta().stream()
            .filter(h -> h.getBytesDelta() > 0L)
            .findFirst()
            .orElse(null);

    if (topGrowingClass != null) {
      reasons.add(
          String.format(
              "%s accounted for %.0f%% of positive histogram byte growth; %.0f%% of total matched-class movement was upward.",
              topGrowingClass.getClassName(),
              topClassShare * 100.0,
              growthDominance * 100.0));
    }

    return EvidenceSignal.available(
        "histogram-growth",
        value,
        "Upward class-histogram dominance and top growing-class concentration.");
  }

  /**
   * Weighted evidence aggregation:
   *
   * <pre>
   * E = sum(w_i * E_i) / sum(w_i)
   * </pre>
   *
   * <p>Only available signals participate. A zero-weight signal is intentionally disabled.
   */
  private static double weightedAvailableEvidence(WeightedSignal... signals) {
    double weightedSum = 0.0;
    double totalWeight = 0.0;

    for (WeightedSignal weightedSignal : signals) {
      EvidenceSignal signal = weightedSignal.signal();
      double weight = weightedSignal.weight();

      if (!signal.available() || weight <= 0.0) {
        continue;
      }

      weightedSum += weight * clamp01(signal.value());
      totalWeight += weight;
    }

    return totalWeight == 0.0 ? 0.0 : weightedSum / totalWeight;
  }

  private static WeightedSignal weighted(EvidenceSignal signal, double weight) {
    return new WeightedSignal(signal, weight);
  }

  private static double nonNegativeWeight(String key, double weight) {
    if (weight < 0.0) {
      throw new IllegalStateException(
          "Configuration '" + key + "' must be greater than or equal to 0.");
    }
    return weight;
  }

  private static double mean(double first, double second) {
    return (clamp01(first) + clamp01(second)) / 2.0;
  }

  /**
   * Builds directional statistics for a historical metric.
   *
   * <pre>
   * movement_i    = value_i - value_(i-1)
   * persistence   = positiveIntervals / totalIntervals
   * retention     = max(netGrowth, 0) / positiveGrowth
   * </pre>
   */
  private static Trend trend(List<Long> values) {
    if (values.size() < 2) {
      return Trend.EMPTY;
    }

    int positiveIntervals = 0;
    long positiveGrowth = 0L;
    long reclaimed = 0L;

    for (int i = 1; i < values.size(); i++) {
      long movement = values.get(i) - values.get(i - 1);
      if (movement > 0L) {
        positiveIntervals++;
        positiveGrowth += movement;
      } else if (movement < 0L) {
        reclaimed += -movement;
      }
    }

    long first = values.get(0);
    long last = values.get(values.size() - 1);
    long netGrowth = last - first;
    double persistence = positiveIntervals / (double) (values.size() - 1);

    return new Trend(first, last, netGrowth, positiveGrowth, reclaimed, persistence);
  }

  /**
   * Window maturity:
   *
   * <pre>
   * maturity = min(1, observedSeconds / WINDOW_SECONDS)
   * </pre>
   */
  private static double windowMaturity(List<JvmHistorySample> window) {
    if (window.size() < 2) {
      return 0.0;
    }

    Instant first = window.get(0).timestamp();
    Instant last = window.get(window.size() - 1).timestamp();

    if (first == null || last == null || !last.isAfter(first)) {
      return 0.0;
    }

    double observedSeconds = Duration.between(first, last).toMillis() / 1000.0;
    return clamp01(observedSeconds / WINDOW_SECONDS);
  }

  private static List<JvmHistorySample> buildWindow(
      List<JvmHistorySample> retainedHistory, JvmHistorySample current) {

    Instant currentTimestamp = current.timestamp();

    if (currentTimestamp == null) {
      List<JvmHistorySample> fallback = new ArrayList<>(retainedHistory);
      fallback.add(current);
      return fallback;
    }

    Instant cutoff = currentTimestamp.minusSeconds(WINDOW_SECONDS);
    List<JvmHistorySample> result = new ArrayList<>();

    for (JvmHistorySample sample : retainedHistory) {
      Instant timestamp = sample.timestamp();
      if (timestamp != null && !timestamp.isBefore(cutoff)) {
        result.add(sample);
      }
    }

    result.add(current);
    return result;
  }

  private static long gcCollections(JvmHistorySample first, JvmHistorySample last) {
    long total = 0L;

    for (Map.Entry<String, Long> current : last.gcCollectionCounts().entrySet()) {
      Long previous = first.gcCollectionCounts().get(current.getKey());
      if (previous != null && current.getValue() >= previous) {
        total += current.getValue() - previous;
      }
    }

    return total;
  }

  private static Recommendation memoryRecommendation(
      double confidence, List<String> reasons) {

    Recommendation recommendation = new Recommendation();
    recommendation.setSeverity(
        confidence >= 80.0
            ? RecommendationSeverity.CRITICAL
            : confidence >= 60.0
                ? RecommendationSeverity.WARNING
                : RecommendationSeverity.INFO);
    recommendation.setConfidence(confidence / 100.0);
    recommendation.setTitle("Persistent memory-retention pattern detected");
    recommendation.setDiagnosis(
        "Historical JVM metrics indicate sustained retention rather than a single allocation burst.");
    recommendation.setProbableCause(
        "Long-lived objects, caches, listeners, queues, or other references may be preventing reclamation.");
    recommendation.setRecommendation(
        "Inspect retained objects and compare heap/class histograms across the same time window; capture a heap dump when confidence remains elevated.");
    recommendation.setEvidence(List.copyOf(reasons));
    return recommendation;
  }

  private static LeakSeverity determineSeverity(double score) {
    if (score >= 80.0) return LeakSeverity.CRITICAL;
    if (score >= 60.0) return LeakSeverity.HIGH;
    if (score >= 30.0) return LeakSeverity.MEDIUM;
    return LeakSeverity.LOW;
  }

  private static void initializeInsufficientHistory(JvmDeltaSnapshot delta) {
    delta.setInstantaneousLeakScore(0.0);
    delta.setLeakScore(0.0);
    delta.setLeakSeverity(LeakSeverity.LOW);
    delta.setHeapGrowthPersistence(0.0);
    delta.setHistoricalWeight(HISTORICAL_WEIGHT);
    delta.setLeakReasons(
        List.of("Insufficient history to evaluate a memory-retention trend."));
    delta.setRecommendations(List.of());
  }

  private record WeightedSignal(EvidenceSignal signal, double weight) {}

  private record Trend(
      long first,
      long last,
      long netGrowth,
      long positiveGrowth,
      long reclaimed,
      double persistence) {

    private static final Trend EMPTY = new Trend(0L, 0L, 0L, 0L, 0L, 0.0);

    boolean available() {
      return first > 0L;
    }

    double retentionRatio() {
      if (positiveGrowth <= 0L || netGrowth <= 0L) {
        return 0.0;
      }
      return clamp01(netGrowth / (double) positiveGrowth);
    }
  }
}
