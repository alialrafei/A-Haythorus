package com.acorp.jvminsight.snapshotcollection.service.delta.rule;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.GcDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.Recommendation;
import com.acorp.jvminsight.snapshotcollection.dto.delta.RecommendationSeverity;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.context.RuleContext;

public class GcPressureRule implements LeakRule {

  @Override
  public void evaluate(
      JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta, RuleContext context) {

    if (delta.getGcDelta() == null) {
      return;
    }

    for (GcDeltaSnapshot gc : delta.getGcDelta()) {

      String name = gc.getGcName();

      /*
       * Interested mainly in old generation collectors.
       */
      if (name.contains("Old") || name.contains("MarkSweep")) {

        double timeGrowth = gc.getCollectionTimeGrowthPercentage();

        double countGrowth = gc.getCollectionCountGrowthPercentage();

        /*
         * Combine both evidences.
         */
        double pressure = (timeGrowth + countGrowth) / 2.0;

        int score = (int) Math.min(pressure, 100);

        if (score <= 0) {
          continue;
        }
        Recommendation recommendation = new Recommendation();
        recommendation.setTitle("GC pressure increasing");

        recommendation.setProbableCause("Garbage collection pause times are increasing.");

        recommendation.setRecommendation(
            "Inspect heap occupancy and consider increasing heap size or fixing memory leaks.");
        context.addScore(score);
        if (score >= 80) recommendation.setSeverity(RecommendationSeverity.CRITICAL);
        else if (score >= 50) recommendation.setSeverity(RecommendationSeverity.WARNING);
        else recommendation.setSeverity(RecommendationSeverity.INFO);
        context.getRecommendations().add(recommendation);
        context
            .getReasons()
            .add(
                String.format(
                    "%s GC pressure increased: count %.2f%%, time %.2f%%",
                    name, countGrowth, timeGrowth));
      }
    }
  }
}
