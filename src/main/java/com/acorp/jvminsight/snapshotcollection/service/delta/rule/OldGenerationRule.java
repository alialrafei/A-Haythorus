package com.acorp.jvminsight.snapshotcollection.service.delta.rule;

import com.acorp.jvminsight.memory.MemoryPoolSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.Recommendation;
import com.acorp.jvminsight.snapshotcollection.dto.delta.RecommendationSeverity;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.context.RuleContext;

public class OldGenerationRule implements LeakRule {

  @Override
  public void evaluate(
      JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta, RuleContext context) {

    MemoryPoolSnapshot previousOldGen = findOldGen(previous);

    MemoryPoolSnapshot currentOldGen = findOldGen(current);

    if (previousOldGen == null || currentOldGen == null) {
      return;
    }

    long previousUsed = previousOldGen.used;

    long currentUsed = currentOldGen.used;

    if (previousUsed <= 0) {
      return;
    }

    long growth = currentUsed - previousUsed;

    if (growth <= 0) {
      return;
    }

    double growthPercentage = ((double) growth / previousUsed) * 100.0;

    int score = (int) Math.min(growthPercentage, 100);

    context.addScore(score);
    Recommendation recommendation = new Recommendation();
    recommendation.setTitle("Old generation growth detected");

    recommendation.setProbableCause(
        "Objects are surviving GC cycles and accumulating in old generation.");

    recommendation.setRecommendation(
        "Analyze heap dumps and inspect long-lived object references.");
    if (score >= 80) recommendation.setSeverity(RecommendationSeverity.CRITICAL);
    else if (score >= 50) recommendation.setSeverity(RecommendationSeverity.WARNING);
    else recommendation.setSeverity(RecommendationSeverity.INFO);
    context.getRecommendations().add(recommendation);
    context
        .getReasons()
        .add(
            String.format(
                "%s increased from %.2f MB to %.2f MB (%.2f%% growth)",
                currentOldGen.name,
                previousUsed / 1024.0 / 1024.0,
                currentUsed / 1024.0 / 1024.0,
                growthPercentage));
  }

  private MemoryPoolSnapshot findOldGen(JvmSnapshot snapshot) {

    if (snapshot.getPools() == null) {
      return null;
    }

    for (MemoryPoolSnapshot pool : snapshot.getPools()) {

      String name = pool.name;

      if (name.contains("Old Gen") || name.contains("Tenured")) {

        return pool;
      }
    }

    return null;
  }
}
