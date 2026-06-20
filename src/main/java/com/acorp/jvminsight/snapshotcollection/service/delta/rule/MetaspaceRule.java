package com.acorp.jvminsight.snapshotcollection.service.delta.rule;

import com.acorp.jvminsight.memory.MemoryPoolSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.Recommendation;
import com.acorp.jvminsight.snapshotcollection.dto.delta.RecommendationSeverity;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.context.RuleContext;

public class MetaspaceRule implements LeakRule {

  @Override
  public void evaluate(
      JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta, RuleContext context) {

    if (previous.getPools() == null || current.getPools() == null) {
      return;
    }

    MemoryPoolSnapshot previousMetaspace = null;
    MemoryPoolSnapshot currentMetaspace = null;

    for (MemoryPoolSnapshot pool : previous.getPools()) {
      if (pool.getName().contains("Metaspace")) {
        previousMetaspace = pool;
        break;
      }
    }

    for (MemoryPoolSnapshot pool : current.getPools()) {
      if (pool.getName().contains("Metaspace")) {
        currentMetaspace = pool;
        break;
      }
    }

    if (previousMetaspace == null || currentMetaspace == null) {
      return;
    }

    long previousUsed = previousMetaspace.getUsed();
    long currentUsed = currentMetaspace.getUsed();

    if (previousUsed <= 0) {
      return;
    }

    double growthPercentage = ((double) (currentUsed - previousUsed) / previousUsed) * 100.0;

    growthPercentage = Math.max(0, growthPercentage);

    int score = (int) Math.min(growthPercentage, 100);
    Recommendation recommendation = new Recommendation();
    recommendation.setTitle("Metaspace growth detected");

    recommendation.setProbableCause("Class metadata usage is increasing.");

    recommendation.setRecommendation(
        "Check for dynamic proxies, classloaders, or hot reload mechanisms.");
    if (score >= 80) recommendation.setSeverity(RecommendationSeverity.CRITICAL);
    else if (score >= 50) recommendation.setSeverity(RecommendationSeverity.WARNING);
    else recommendation.setSeverity(RecommendationSeverity.INFO);
    context.addScore(score);
    context.getRecommendations().add(recommendation);
    context.getReasons().add(String.format("Metaspace grew by %.2f%%", growthPercentage));
  }
}
