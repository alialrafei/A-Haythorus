package com.acorp.jvminsight.snapshotcollection.service.delta.rule;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.Recommendation;
import com.acorp.jvminsight.snapshotcollection.dto.delta.RecommendationSeverity;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.context.RuleContext;

public class HeapGrowthRule implements LeakRule {

  private static final long MB = 1024L * 1024L;

  @Override
  public void evaluate(
      JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta, RuleContext context) {

    long previousHeap = previous.getMemory().heapUsed;
    long currentHeap = current.getMemory().heapUsed;

    if (previousHeap <= 0) {
      return;
    }

    long heapDelta = currentHeap - previousHeap;

    if (heapDelta <= 0) {
      return;
    }

    double growthPercentage = ((double) heapDelta / previousHeap) * 100.0;

    int score = (int) Math.min(Math.max(growthPercentage, 0), 100);

    context.addScore(score);
    Recommendation recommendation = new Recommendation();

    recommendation.setSeverity(RecommendationSeverity.WARNING);

    recommendation.setTitle("Investigate heap growth");

    recommendation.setProbableCause("Heap usage increased significantly between snapshots.");

    recommendation.setRecommendation("Capture heap dumps and inspect dominant objects.");
    if (score >= 80) recommendation.setSeverity(RecommendationSeverity.CRITICAL);
    else if (score >= 50) recommendation.setSeverity(RecommendationSeverity.WARNING);
    else recommendation.setSeverity(RecommendationSeverity.INFO);
    context.getRecommendations().add(recommendation);
    context
        .getReasons()
        .add(
            String.format(
                "Heap increased from %.2f MB to %.2f MB (%.2f%% growth)",
                previousHeap / (double) MB, currentHeap / (double) MB, growthPercentage));
  }
}
