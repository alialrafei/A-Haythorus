package com.acorp.jvminsight.snapshotcollection.service.delta;
import static com.acorp.jvminsight.util.GrowthCalculator.bytesToMb;
import static com.acorp.jvminsight.util.GrowthCalculator.clamp01;
import static com.acorp.jvminsight.util.GrowthCalculator.clamp100;
import static com.acorp.jvminsight.util.GrowthCalculator.validatedAlpha;
import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.snapshotcollection.dto.JvmHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
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
 * History-aware memory-leak analyzer.
 *
 * <p>The analyzer deliberately separates:
 *
 * <ul>
 *   <li>raw JVM measurements,
 *   <li>normalized evidence in the range [0, 1],
 *   <li>the final memory-leak score in the range [0, 100].
 * </ul>
 *
 * <p>No evidence signal uses an arbitrary "20% means maximum evidence" threshold.
 * Instead, evidence is built from naturally normalized ratios such as persistence,
 * retention, reclamation and histogram movement.
 */
public final class HistoricalLeakAnalyzer {

  private static final long WINDOW_SECONDS =
      Math.max(
          10L,
          ConfigLoader.getLong(
              "leak.window.seconds",
              60L));

  private static final double ALPHA =
      validatedAlpha(
          ConfigLoader.getDouble(
              "leak.ewma.alpha",
              0.35));

  private static final double HISTORICAL_WEIGHT =
      1.0 - ALPHA;

  private HistoricalLeakAnalyzer() {}

  public static void analyze(
      List<JvmHistorySample> retainedHistory,
      JvmSnapshot current,
      JvmDeltaSnapshot delta) {

    List<JvmHistorySample> window =
        buildWindow(
            retainedHistory,
            JvmHistorySample.from(current));

    if (window.size() < 2) {
      initializeInsufficientHistory(delta);
      return;
    }

    List<String> reasons =
        new ArrayList<>();

    List<Recommendation> recommendations =
        new ArrayList<>();

    /*
     * ---------------------------------------------------------
     * Build historical trends
     * ---------------------------------------------------------
     */

    Trend heapTrend =
        trend(
            window.stream()
                .map(JvmHistorySample::heapUsed)
                .toList());

    Trend oldGenTrend =
        trend(
            window.stream()
                .map(JvmHistorySample::oldGenerationUsed)
                .filter(value -> value > 0)
                .toList());

    long gcCollections =
        gcCollections(
            window.get(0),
            window.get(window.size() - 1));

    delta.setHeapGrowthPersistence(
        heapTrend.persistence());

    delta.setWindowHeapGrowthBytes(
        heapTrend.netGrowth());

    delta.setWindowGcCollections(
        gcCollections);

    /*
     * ---------------------------------------------------------
     * Calculate normalized evidence
     *
     * Every evidence value must remain inside [0, 1].
     * ---------------------------------------------------------
     */

    double heapEvidence =
        heapEvidence(
            heapTrend,
            reasons);

    double oldGenEvidence =
        oldGenerationEvidence(
            oldGenTrend,
            reasons);

    double gcEvidence =
        gcReclaimEvidence(
            heapTrend,
            gcCollections,
            reasons);

    double histogramEvidence =
        histogramEvidence(
            delta,
            reasons);

    /*
     * Equal weighting for now.
     *
     * S = (E_heap + E_oldGen + E_gc + E_histogram) / 4
     *
     * Because every E is in [0,1], the result is also [0,1].
     */
    double normalizedEvidence =
        averageEvidence(
            heapEvidence,
            oldGenEvidence,
            gcEvidence,
            histogramEvidence);

    /*
     * Convert [0,1] evidence to [0,100].
     */
    double rawEvidenceScore =
        normalizedEvidence * 100.0;

    /*
     * Window maturity reduces confidence while the configured
     * historical window is still being filled.
     */
    double maturity =
        windowMaturity(window);

    double instantaneousScore =
        rawEvidenceScore * maturity;

    /*
     * EWMA smooths the score across successive analysis windows:
     *
     * confidence(t)
     *   = alpha * instantaneous(t)
     *   + (1 - alpha) * confidence(t - 1)
     */
    double previousConfidence =
        retainedHistory.isEmpty()
            ? 0.0
            : retainedHistory
                .get(retainedHistory.size() - 1)
                .leakConfidence();

    double confidence =
        clamp100(
            ALPHA * instantaneousScore
                + HISTORICAL_WEIGHT * previousConfidence);

    delta.setInstantaneousLeakScore(
        instantaneousScore);

    delta.setLeakScore(
        confidence);

    delta.setHistoricalWeight(
        HISTORICAL_WEIGHT);

    delta.setLeakSeverity(
        determineSeverity(confidence));

    delta.setLeakReasons(
        reasons);

    if (confidence >= 30.0) {
      recommendations.add(
          memoryRecommendation(
              confidence,
              reasons));
    }

    delta.setRecommendations(
        recommendations);
  }

  /*
   * =========================================================
   * EVIDENCE FORMULAS
   * =========================================================
   *
   * This section contains the mathematical definitions used
   * by the memory analyzer.
   *
   * Every formula returns evidence E in:
   *
   *             0 <= E <= 1
   *
   * 0 means no evidence.
   * 1 means strongest evidence under that particular metric.
   *
   * There are deliberately no arbitrary percentage saturation
   * thresholds here.
   * =========================================================
   */

  /**
   * Heap evidence combines two independent properties of heap movement:
   *
   * <pre>
   * persistence =
   *     positiveIntervals / totalIntervals
   *
   * retention =
   *     max(netGrowth, 0) / positiveGrowth
   *
   * heapEvidence =
   *     (persistence + retention) / 2
   * </pre>
   *
   * <p>Persistence answers:
   *
   * <p>"How consistently did heap usage move upward?"
   *
   * <p>Retention answers:
   *
   * <p>"Of all upward movement observed, how much remained after downward movement?"
   *
   * <p>Example:
   *
   * <pre>
   * values = [100, 130, 120, 150]
   *
   * positiveGrowth = 30 + 30 = 60
   * reclaimed      = 10
   * netGrowth      = 50
   *
   * persistence = 2 / 3 = 0.667
   * retention   = 50 / 60 = 0.833
   *
   * evidence = (0.667 + 0.833) / 2
   *          = 0.75
   * </pre>
   */
  private static double heapEvidence(
      Trend trend,
      List<String> reasons) {

    if (!trend.available()
        || trend.netGrowth() <= 0) {
      return 0.0;
    }

    double evidence =
        averageEvidence(
            trend.persistence(),
            trend.retentionRatio());

    reasons.add(
        String.format(
            "Heap retained %.2f MB; %.0f%% of intervals moved upward and %.0f%% of positive growth remained retained.",
            bytesToMb(trend.netGrowth()),
            trend.persistence() * 100.0,
            trend.retentionRatio() * 100.0));

    return evidence;
  }

  /**
   * Old-generation evidence uses the same mathematical structure as heap evidence:
   *
   * <pre>
   * oldGenEvidence =
   *     (oldGenPersistence + oldGenRetentionRatio) / 2
   * </pre>
   *
   * <p>Old-generation growth is especially useful because objects surviving
   * long enough to reach old generation are more relevant to long-lived
   * retention than short-lived allocation noise.
   */
  private static double oldGenerationEvidence(
      Trend trend,
      List<String> reasons) {

    if (!trend.available()
        || trend.netGrowth() <= 0) {
      return 0.0;
    }

    double evidence =
        averageEvidence(
            trend.persistence(),
            trend.retentionRatio());

    reasons.add(
        String.format(
            "Old-generation usage retained %.2f MB; %.0f%% of intervals moved upward and %.0f%% of positive growth remained retained.",
            bytesToMb(trend.netGrowth()),
            trend.persistence() * 100.0,
            trend.retentionRatio() * 100.0));

    return evidence;
  }

  /**
   * GC evidence asks whether memory growth remains despite actual GC activity.
   *
   * <pre>
   * reclaimRatio =
   *     reclaimedBytes / positiveGrowthBytes
   *
   * retentionRatio =
   *     1 - reclaimRatio
   *
   * gcEvidence =
   *     retentionRatio * heapPersistence
   * </pre>
   *
   * <p>The result is naturally bounded in [0,1].
   *
   * <p>Multiplying by persistence prevents a single upward burst followed by
   * reclamation from being treated like persistent retention.
   *
   * <p>If no GC occurred in the window, this signal returns zero because we
   * did not observe a reclamation opportunity and therefore cannot use GC
   * behavior as evidence.
   *
   * <p>Important: this is still a heuristic because the current history does
   * not correlate individual downward heap movements with specific GC events.
   * A stronger future implementation should compare post-GC live-set floors.
   */
  private static double gcReclaimEvidence(
      Trend heapTrend,
      long gcCollections,
      List<String> reasons) {

    if (gcCollections <= 0
        || !heapTrend.available()
        || heapTrend.positiveGrowth() <= 0) {
      return 0.0;
    }

    double reclaimRatio =
        clamp01(
            heapTrend.reclaimed()
                / (double) heapTrend.positiveGrowth());

    double retentionRatio =
        1.0 - reclaimRatio;

    double evidence =
        retentionRatio
            * heapTrend.persistence();

    if (evidence > 0.0) {
      reasons.add(
          String.format(
              "%d GC collection(s) occurred; %.0f%% of observed positive heap growth was reclaimed and %.0f%% remained retained.",
              gcCollections,
              reclaimRatio * 100.0,
              retentionRatio * 100.0));
    }

    return clamp01(evidence);
  }

  /**
   * Histogram evidence measures whether object-size movement is mostly upward
   * and whether that growth is concentrated in one dominant class.
   *
   * <pre>
   * positiveBytes =
   *     sum(max(classBytesDelta, 0))
   *
   * reclaimedBytes =
   *     sum(max(-classBytesDelta, 0))
   *
   * growthDominance =
   *     positiveBytes / (positiveBytes + reclaimedBytes)
   *
   * topClassShare =
   *     largestPositiveClassDelta / positiveBytes
   *
   * histogramEvidence =
   *     (growthDominance + topClassShare) / 2
   * </pre>
   *
   * <p>Both terms are naturally normalized:
   *
   * <ul>
   *   <li>growthDominance tells us whether histogram movement is mostly upward,
   *   <li>topClassShare tells us whether one class explains a large part of that growth.
   * </ul>
   *
   * <p>This remains pairwise evidence because the lightweight historical store
   * currently does not retain class histograms. A future version can strengthen
   * this by measuring class-growth persistence across several histogram samples.
   */
  private static double histogramEvidence(
      JvmDeltaSnapshot delta,
      List<String> reasons) {

    List<HistogramDelta> histogram =
        delta.getHistogramDelta();

    if (histogram == null
        || histogram.isEmpty()) {
      return 0.0;
    }

    double positiveBytes = 0.0;
    double reclaimedBytes = 0.0;

    HistogramDelta topGrowingClass =
        null;

    long topPositiveDelta =
        0L;

    for (HistogramDelta classDelta : histogram) {

      long bytesDelta =
          classDelta.getBytesDelta();

      if (bytesDelta > 0) {
        positiveBytes += bytesDelta;

        if (bytesDelta > topPositiveDelta) {
          topPositiveDelta = bytesDelta;
          topGrowingClass = classDelta;
        }

      } else if (bytesDelta < 0) {
        reclaimedBytes += -((double) bytesDelta);
      }
    }

    if (positiveBytes <= 0.0) {
      return 0.0;
    }

    double totalMovement =
        positiveBytes
            + reclaimedBytes;

    double growthDominance =
        totalMovement <= 0.0
            ? 0.0
            : positiveBytes / totalMovement;

    double topClassShare =
        topPositiveDelta
            / positiveBytes;

    double evidence =
        averageEvidence(
            growthDominance,
            topClassShare);

    if (topGrowingClass != null) {
      reasons.add(
          String.format(
              "%s accounted for %.0f%% of positive histogram byte growth; %.0f%% of total histogram movement was upward.",
              topGrowingClass.getClassName(),
              topClassShare * 100.0,
              growthDominance * 100.0));
    }

    return clamp01(evidence);
  }

  /**
   * Equal-weight evidence aggregation.
   *
   * <pre>
   * E =
   *     (E1 + E2 + ... + En) / n
   * </pre>
   *
   * <p>Because each individual evidence value is already normalized into [0,1],
   * the arithmetic mean also remains inside [0,1].
   *
   * <p>This is the correct default while we have no application-specific reason
   * to prefer one evidence source over another.
   *
   * <p>Later this method can evolve into:
   *
   * <pre>
   * E =
   *     sum(w_i * E_i) / sum(w_i)
   * </pre>
   *
   * without changing the individual evidence formulas.
   */
  private static double averageEvidence(
      double... evidenceValues) {

    if (evidenceValues.length == 0) {
      return 0.0;
    }

    double total =
        0.0;

    for (double evidence : evidenceValues) {
      total += clamp01(evidence);
    }

    return total
        / evidenceValues.length;
  }

  /*
   * =========================================================
   * TREND CALCULATION
   * =========================================================
   */

  /**
   * Converts a sequence of measurements into direction and retention statistics.
   *
   * <p>For each interval:
   *
   * <pre>
   * movement_i =
   *     value_i - value_(i-1)
   * </pre>
   *
   * Positive movements contribute to {@code positiveGrowth}.
   *
   * Negative movements contribute to {@code reclaimed}.
   *
   * <p>Persistence:
   *
   * <pre>
   * persistence =
   *     positiveIntervals / totalIntervals
   * </pre>
   *
   * <p>Retention ratio:
   *
   * <pre>
   * retentionRatio =
   *     max(netGrowth, 0) / positiveGrowth
   * </pre>
   */
  private static Trend trend(
      List<Long> values) {

    if (values.size() < 2) {
      return Trend.EMPTY;
    }

    int positiveIntervals =
        0;

    long positiveGrowth =
        0L;

    long reclaimed =
        0L;

    for (int i = 1;
        i < values.size();
        i++) {

      long movement =
          values.get(i)
              - values.get(i - 1);

      if (movement > 0) {
        positiveIntervals++;
        positiveGrowth += movement;

      } else if (movement < 0) {
        reclaimed += -movement;
      }
    }

    long first =
        values.get(0);

    long last =
        values.get(
            values.size() - 1);

    long netGrowth =
        last - first;

    double persistence =
        positiveIntervals
            / (double) (values.size() - 1);

    return new Trend(
        first,
        last,
        netGrowth,
        positiveGrowth,
        reclaimed,
        persistence);
  }

  /*
   * =========================================================
   * WINDOW / TEMPORAL CONFIDENCE
   * =========================================================
   */

  /**
   * Window maturity represents how much of the configured historical horizon
   * has actually been observed.
   *
   * <pre>
   * maturity =
   *     min(
   *         1,
   *         observedSeconds / WINDOW_SECONDS
   *     )
   * </pre>
   *
   * <p>Example for a 60-second analysis window:
   *
   * <pre>
   * 15 seconds -> 0.25
   * 30 seconds -> 0.50
   * 60 seconds -> 1.00
   * </pre>
   *
   * <p>If timestamps are invalid, maturity cannot be calculated reliably.
   * Rather than introducing another arbitrary sample-count constant, this
   * implementation returns zero maturity in that exceptional case.
   */
  private static double windowMaturity(
      List<JvmHistorySample> window) {

    if (window.size() < 2) {
      return 0.0;
    }

    Instant first =
        window.get(0)
            .timestamp();

    Instant last =
        window.get(window.size() - 1)
            .timestamp();

    if (first == null
        || last == null
        || !last.isAfter(first)) {
      return 0.0;
    }

    double observedSeconds =
        Duration.between(
                first,
                last)
            .toMillis()
            / 1000.0;

    return clamp01(
        observedSeconds
            / WINDOW_SECONDS);
  }

  private static List<JvmHistorySample> buildWindow(
      List<JvmHistorySample> retainedHistory,
      JvmHistorySample current) {

    Instant currentTimestamp =
        current.timestamp();

    if (currentTimestamp == null) {
      List<JvmHistorySample> fallback =
          new ArrayList<>(retainedHistory);

      fallback.add(current);

      return fallback;
    }

    Instant cutoff =
        currentTimestamp.minusSeconds(
            WINDOW_SECONDS);

    List<JvmHistorySample> result =
        new ArrayList<>();

    for (JvmHistorySample sample
        : retainedHistory) {

      Instant timestamp =
          sample.timestamp();

      if (timestamp != null
          && !timestamp.isBefore(cutoff)) {
        result.add(sample);
      }
    }

    result.add(current);

    return result;
  }

  /*
   * =========================================================
   * GC HELPERS
   * =========================================================
   */

  private static long gcCollections(
      JvmHistorySample first,
      JvmHistorySample last) {

    long total =
        0L;

    for (Map.Entry<String, Long> current
        : last.gcCollectionCounts().entrySet()) {

      Long previous =
          first.gcCollectionCounts()
              .get(current.getKey());

      if (previous != null
          && current.getValue() >= previous) {

        total +=
            current.getValue()
                - previous;
      }
    }

    return total;
  }

  /*
   * =========================================================
   * RECOMMENDATIONS / SEVERITY
   * =========================================================
   */

  private static Recommendation memoryRecommendation(
      double confidence,
      List<String> reasons) {

    Recommendation recommendation =
        new Recommendation();

    recommendation.setSeverity(
        confidence >= 80
            ? RecommendationSeverity.CRITICAL
            : confidence >= 60
                ? RecommendationSeverity.WARNING
                : RecommendationSeverity.INFO);

    recommendation.setConfidence(
        confidence / 100.0);

    recommendation.setTitle(
        "Persistent memory-retention pattern detected");

    recommendation.setDiagnosis(
        "Historical JVM metrics indicate sustained retention rather than a single allocation burst.");

    recommendation.setProbableCause(
        "Long-lived objects, caches, listeners, queues, or other references may be preventing reclamation.");

    recommendation.setRecommendation(
        "Inspect retained objects and compare heap/class histograms across the same time window; capture a heap dump when confidence remains elevated.");

    recommendation.setEvidence(
        List.copyOf(reasons));

    return recommendation;
  }

  private static LeakSeverity determineSeverity(
      double score) {

    if (score >= 80.0) {
      return LeakSeverity.CRITICAL;
    }

    if (score >= 60.0) {
      return LeakSeverity.HIGH;
    }

    if (score >= 30.0) {
      return LeakSeverity.MEDIUM;
    }

    return LeakSeverity.LOW;
  }

 
  private static void initializeInsufficientHistory(
      JvmDeltaSnapshot delta) {

    delta.setInstantaneousLeakScore(
        0.0);

    delta.setLeakScore(
        0.0);

    delta.setLeakSeverity(
        LeakSeverity.LOW);

    delta.setHeapGrowthPersistence(
        0.0);

    delta.setHistoricalWeight(
        HISTORICAL_WEIGHT);

    delta.setLeakReasons(
        List.of(
            "Insufficient history to evaluate a memory-retention trend."));

    delta.setRecommendations(
        List.of());
  }

  /**
   * Compact representation of the directional behavior of one historical metric.
   *
   * @param first first observed value
   * @param last latest observed value
   * @param netGrowth {@code last - first}
   * @param positiveGrowth sum of all positive interval movements
   * @param reclaimed sum of the absolute value of all negative interval movements
   * @param persistence fraction of intervals whose movement was positive
   */
  private record Trend(
      long first,
      long last,
      long netGrowth,
      long positiveGrowth,
      long reclaimed,
      double persistence) {

    private static final Trend EMPTY =
        new Trend(
            0L,
            0L,
            0L,
            0L,
            0L,
            0.0);

    boolean available() {
      return first > 0L;
    }

    /**
     * Fraction of all positive movement that remains as net retained growth.
     *
     * <pre>
     * retentionRatio =
     *     max(netGrowth, 0) / positiveGrowth
     * </pre>
     *
     * <p>Because net positive growth cannot exceed the sum of positive movements,
     * this ratio naturally lies in [0,1].
     */
    double retentionRatio() {

      if (positiveGrowth <= 0L
          || netGrowth <= 0L) {
        return 0.0;
      }

      return clamp01(
          netGrowth
              / (double) positiveGrowth);
    }
  }
}