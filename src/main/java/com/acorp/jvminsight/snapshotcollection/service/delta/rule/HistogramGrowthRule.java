package com.acorp.jvminsight.snapshotcollection.service.delta.rule;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.HistogramDelta;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.Recommendation;
import com.acorp.jvminsight.snapshotcollection.dto.delta.RecommendationSeverity;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.context.RuleContext;

public class HistogramGrowthRule implements LeakRule {

  @Override
  public void evaluate(
      JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta, RuleContext context) {

    if (delta.getHistogramDelta() == null || delta.getHistogramDelta().isEmpty()) {
      return;
    }

    HistogramDelta top = delta.getHistogramDelta().get(0);

    if (top.getPreviousBytes() <= 0) {
      return;
    }

    double growthPercentage = ((double) top.getBytesDelta() / top.getPreviousBytes()) * 100.0;

    growthPercentage = Math.max(0, growthPercentage);

    int score = (int) Math.min(growthPercentage, 100);

    context.addScore(score);
    Recommendation recommendation = new Recommendation();
    recommendation.setTitle("Object accumulation detected");

    recommendation.setProbableCause(top.getClassName() + " is growing continuously.");

    recommendation.setRecommendation("Inspect the object lifecycle and cache eviction policy.");
    if (score >= 80) recommendation.setSeverity(RecommendationSeverity.CRITICAL);
    else if (score >= 50) recommendation.setSeverity(RecommendationSeverity.WARNING);
    else recommendation.setSeverity(RecommendationSeverity.INFO);
    context.getRecommendations().add(recommendation);
    context
        .getReasons()
        .add(
            String.format(
                "%s grew from %.2f MB to %.2f MB (%.2f%% growth)",
                top.getClassName(),
                top.getPreviousBytes() / 1024.0 / 1024.0,
                top.getCurrentBytes() / 1024.0 / 1024.0,
                growthPercentage));
  }
}
