package com.acorp.jvminsight.snapshotcollection.service.delta.rule;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.Recommendation;
import com.acorp.jvminsight.snapshotcollection.dto.delta.RecommendationSeverity;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.context.RuleContext;

public class ThreadLeakRule implements LeakRule {

  @Override
  public void evaluate(
      JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta, RuleContext context) {

    long previousThreadCount = previous.getThreadCount();
    long currentThreadCount = current.getThreadCount();

    if (previousThreadCount <= 0) {
      return;
    }

    long threadDelta = currentThreadCount - previousThreadCount;

    if (threadDelta <= 0) {
      return;
    }

    double growthPercentage = ((double) threadDelta / previousThreadCount) * 100.0;

    int score = (int) Math.min(growthPercentage, 100);

    context.addScore(score);
    Recommendation recommendation = new Recommendation();
    recommendation.setTitle("Investigate thread creation");

    recommendation.setProbableCause("Thread count is growing continuously.");

    recommendation.setRecommendation(
        "Look for unbounded thread pools or missing executor shutdown.");
    if (score >= 80) recommendation.setSeverity(RecommendationSeverity.CRITICAL);
    else if (score >= 50) recommendation.setSeverity(RecommendationSeverity.WARNING);
    else recommendation.setSeverity(RecommendationSeverity.INFO);
    context.getRecommendations().add(recommendation);
    context
        .getReasons()
        .add(
            String.format(
                "Thread count increased from %d to %d (%.2f%% growth)",
                previousThreadCount, currentThreadCount, growthPercentage));
  }
}
