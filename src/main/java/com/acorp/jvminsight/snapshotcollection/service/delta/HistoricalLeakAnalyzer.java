package com.acorp.jvminsight.snapshotcollection.service.delta;

import com.acorp.jvminsight.snapshotcollection.dto.JvmHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.HistogramDelta;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.LeakSeverity;
import com.acorp.jvminsight.snapshotcollection.dto.delta.Recommendation;
import com.acorp.jvminsight.snapshotcollection.dto.delta.RecommendationSeverity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** History-aware, explainable leak-confidence model. */
public final class HistoricalLeakAnalyzer {

  private static final int WINDOW_SIZE = 12;
  private static final double ALPHA = 0.35;
  private static final double HISTORICAL_WEIGHT = 1.0 - ALPHA;

  private HistoricalLeakAnalyzer() {}

  public static void analyze(
      List<JvmHistorySample> retainedHistory, JvmSnapshot current, JvmDeltaSnapshot delta) {

    List<JvmHistorySample> window = buildWindow(retainedHistory, JvmHistorySample.from(current));
    if (window.size() < 2) {
      delta.setInstantaneousLeakScore(0);
      delta.setLeakScore(0);
      delta.setLeakSeverity(LeakSeverity.LOW);
      delta.setHeapGrowthPersistence(0.0);
      delta.setHistoricalWeight(HISTORICAL_WEIGHT);
      delta.setLeakReasons(List.of("Insufficient history to evaluate a memory-retention trend."));
      delta.setRecommendations(List.of());
      return;
    }

    List<String> reasons = new ArrayList<>();
    List<Recommendation> recommendations = new ArrayList<>();

    Trend heapTrend = trend(window.stream().map(JvmHistorySample::heapUsed).toList());
    delta.setHeapGrowthPersistence(heapTrend.persistence());
    delta.setWindowHeapGrowthBytes(heapTrend.netGrowth());

    int heapScore = heapEvidence(heapTrend, reasons);

    Trend oldGenTrend =
        trend(window.stream().map(JvmHistorySample::oldGenerationUsed).filter(v -> v > 0).toList());
    int oldGenScore = oldGenerationEvidence(oldGenTrend, reasons);

    long gcCollections = gcCollections(window.get(0), window.get(window.size() - 1));
    delta.setWindowGcCollections(gcCollections);
    int gcScore = gcReclaimEvidence(heapTrend, gcCollections, reasons);

    int histogramScore = histogramEvidence(delta, reasons);
    int threadScore = threadEvidence(window, reasons);

    int rawEvidence = clamp(heapScore + oldGenScore + gcScore + histogramScore + threadScore);

    double maturity = Math.min(1.0, (window.size() - 1) / 5.0);
    int instantaneous = (int) Math.round(rawEvidence * maturity);

    int previousConfidence = retainedHistory.isEmpty() ? 0 : retainedHistory.get(retainedHistory.size() - 1).leakConfidence();
    int confidence = clamp((int) Math.round(ALPHA * instantaneous + HISTORICAL_WEIGHT * previousConfidence));

    delta.setInstantaneousLeakScore(instantaneous);
    delta.setLeakScore(confidence);
    delta.setHistoricalWeight(HISTORICAL_WEIGHT);
    delta.setLeakSeverity(determineSeverity(confidence));
    delta.setLeakReasons(reasons);

    if (confidence >= 30) {
      recommendations.add(memoryRecommendation(confidence, reasons));
    }
    delta.setRecommendations(recommendations);
  }

  private static List<JvmHistorySample> buildWindow(
      List<JvmHistorySample> retainedHistory, JvmHistorySample current) {
    List<JvmHistorySample> result = new ArrayList<>();
    int from = Math.max(0, retainedHistory.size() - (WINDOW_SIZE - 1));
    result.addAll(retainedHistory.subList(from, retainedHistory.size()));
    result.add(current);
    return result;
  }

  private static Trend trend(List<Long> values) {
    if (values.size() < 2) return Trend.EMPTY;

    int positiveIntervals = 0;
    long positiveGrowth = 0;
    long reclaimed = 0;

    for (int i = 1; i < values.size(); i++) {
      long movement = values.get(i) - values.get(i - 1);
      if (movement > 0) {
        positiveIntervals++;
        positiveGrowth += movement;
      } else if (movement < 0) {
        reclaimed += -movement;
      }
    }

    long first = values.get(0);
    long last = values.get(values.size() - 1);
    double persistence = positiveIntervals / (double) (values.size() - 1);
    double netGrowthPercentage = first <= 0 ? 0.0 : ((double) (last - first) / first) * 100.0;

    return new Trend(
        first,
        last,
        last - first,
        positiveGrowth,
        reclaimed,
        persistence,
        netGrowthPercentage);
  }

  private static int heapEvidence(Trend trend, List<String> reasons) {
    if (!trend.available() || trend.netGrowth() <= 0) return 0;

    int persistencePoints = (int) Math.round(15.0 * trend.persistence());
    int growthPoints =
        (int) Math.round(15.0 * normalizePositive(trend.netGrowthPercentage(), 20.0));

    reasons.add(
        String.format(
            "Heap retained %.2f MB across the window; %.0f%% of intervals moved upward.",
            trend.netGrowth() / 1024.0 / 1024.0,
            trend.persistence() * 100.0));
    return persistencePoints + growthPoints;
  }

  private static int oldGenerationEvidence(Trend trend, List<String> reasons) {
    if (!trend.available() || trend.netGrowth() <= 0) return 0;

    int persistencePoints = (int) Math.round(12.0 * trend.persistence());
    int growthPoints =
        (int) Math.round(13.0 * normalizePositive(trend.netGrowthPercentage(), 20.0));

    reasons.add(
        String.format(
            "Old-generation usage retained %.2f MB across the analysis window.",
            trend.netGrowth() / 1024.0 / 1024.0));
    return persistencePoints + growthPoints;
  }

  private static int gcReclaimEvidence(
      Trend heapTrend, long gcCollections, List<String> reasons) {
    if (gcCollections <= 0 || !heapTrend.available() || heapTrend.positiveGrowth() <= 0) return 0;

    double reclaimRatio =
        Math.min(1.0, heapTrend.reclaimed() / (double) Math.max(1L, heapTrend.positiveGrowth()));
    double retentionRatio = 1.0 - reclaimRatio;
    int score = (int) Math.round(20.0 * retentionRatio * heapTrend.persistence());

    if (score > 0) {
      reasons.add(
          String.format(
              "%d GC collection(s) occurred while only %.0f%% of observed positive heap growth was reclaimed.",
              gcCollections,
              reclaimRatio * 100.0));
    }
    return score;
  }

  private static int histogramEvidence(JvmDeltaSnapshot delta, List<String> reasons) {
    if (delta.getHistogramDelta() == null || delta.getHistogramDelta().isEmpty()) return 0;

    HistogramDelta top =
        delta.getHistogramDelta().stream()
            .filter(h -> h.getBytesDelta() > 0 && h.getPreviousBytes() > 0)
            .findFirst()
            .orElse(null);
    if (top == null) return 0;

    double growth = (top.getBytesDelta() / (double) top.getPreviousBytes()) * 100.0;
    int score = (int) Math.round(15.0 * normalizePositive(growth, 50.0));
    if (score > 0) {
      reasons.add(
          String.format(
              "%s retained an additional %.2f MB between the latest histogram samples.",
              top.getClassName(), top.getBytesDelta() / 1024.0 / 1024.0));
    }
    return score;
  }

  private static int threadEvidence(List<JvmHistorySample> window, List<String> reasons) {
    long growth = window.get(window.size() - 1).threadCount() - window.get(0).threadCount();
    if (growth <= 0) return 0;

    int score = (int) Math.min(10, growth);
    reasons.add(String.format("Thread count increased by %d across the analysis window.", growth));
    return score;
  }

  private static long gcCollections(JvmHistorySample first, JvmHistorySample last) {
    long total = 0;
    for (Map.Entry<String, Long> current : last.gcCollectionCounts().entrySet()) {
      Long previous = first.gcCollectionCounts().get(current.getKey());
      if (previous != null && current.getValue() >= previous) {
        total += current.getValue() - previous;
      }
    }
    return total;
  }

  private static Recommendation memoryRecommendation(int confidence, List<String> reasons) {
    Recommendation recommendation = new Recommendation();
    recommendation.setSeverity(
        confidence >= 80
            ? RecommendationSeverity.CRITICAL
            : confidence >= 60 ? RecommendationSeverity.WARNING : RecommendationSeverity.INFO);
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

  private static double normalizePositive(double value, double fullScale) {
    return value <= 0 ? 0.0 : Math.min(1.0, value / fullScale);
  }

  private static int clamp(int value) {
    return Math.max(0, Math.min(100, value));
  }

  private static LeakSeverity determineSeverity(int score) {
    if (score >= 80) return LeakSeverity.CRITICAL;
    if (score >= 60) return LeakSeverity.HIGH;
    if (score >= 30) return LeakSeverity.MEDIUM;
    return LeakSeverity.LOW;
  }

  private record Trend(
      long first,
      long last,
      long netGrowth,
      long positiveGrowth,
      long reclaimed,
      double persistence,
      double netGrowthPercentage) {

    private static final Trend EMPTY = new Trend(0, 0, 0, 0, 0, 0.0, 0.0);

    boolean available() {
      return first > 0;
    }
  }
}
