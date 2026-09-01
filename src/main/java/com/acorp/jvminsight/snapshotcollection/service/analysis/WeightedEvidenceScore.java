package com.acorp.jvminsight.snapshotcollection.service.analysis;

import com.acorp.jvminsight.snapshotcollection.dto.analysis.EvidenceSignal;

/**
 * Shared weighted-evidence math used by every analyzer.
 *
 * <pre>
 * score = sum(w_i * E_i) / sum(w_i)
 * </pre>
 *
 * <p>Only available signals with positive weights participate. Evidence is clamped to [0,1].
 * A weight of zero disables a signal. Negative weights are invalid.
 */
public final class WeightedEvidenceScore {

  private WeightedEvidenceScore() {}

  public static WeightedSignal weighted(EvidenceSignal signal, double weight) {
    if (weight < 0.0) {
      throw new IllegalArgumentException("Evidence weights must be >= 0.");
    }
    return new WeightedSignal(signal, weight);
  }

  public static double calculate(WeightedSignal... signals) {
    double weightedSum = 0.0;
    double totalWeight = 0.0;

    for (WeightedSignal item : signals) {
      if (item.signal() == null || !item.signal().available() || item.weight() <= 0.0) {
        continue;
      }

      weightedSum += item.weight() * clamp01(item.signal().value());
      totalWeight += item.weight();
    }

    return totalWeight == 0.0 ? 0.0 : weightedSum / totalWeight;
  }

  public static double requireNonNegativeWeight(String key, double value) {
    if (value < 0.0) {
      throw new IllegalStateException(
          "Configuration '" + key + "' must be greater than or equal to 0.");
    }
    return value;
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  public record WeightedSignal(EvidenceSignal signal, double weight) {}
}
