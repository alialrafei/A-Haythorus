package com.acorp.jvminsight.snapshotcollection.service.delta.strategy;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.LeakSeverity;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaComputationStrategy;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.GcPressureRule;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.HeapGrowthRule;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.HistogramGrowthRule;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.LeakRule;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.MetaspaceRule;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.OldGenerationRule;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.ThreadLeakRule;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.context.RuleContext;
import java.util.List;

public class LeakDetectionStrategy implements DeltaComputationStrategy {

  private static final List<LeakRule> RULES =
      List.of(
          new HeapGrowthRule(),
          new ThreadLeakRule(),
          new OldGenerationRule(),
          new MetaspaceRule(),
          new HistogramGrowthRule(),
          new GcPressureRule());

  @Override
  public void compute(JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta) {

    RuleContext context = new RuleContext();

    for (LeakRule rule : RULES) {
      rule.evaluate(previous, current, delta, context);
    }

    int finalScore = RULES.isEmpty() ? 0 : context.getScore() / RULES.size();

    delta.setLeakScore(finalScore);
    delta.setLeakReasons(context.getReasons());
    delta.setRecommendations(context.getRecommendations());
    delta.setLeakSeverity(determineSeverity(finalScore));
  }

  private LeakSeverity determineSeverity(int score) {

    if (score >= 80) {
      return LeakSeverity.CRITICAL;
    }

    if (score >= 60) {
      return LeakSeverity.HIGH;
    }

    if (score >= 30) {
      return LeakSeverity.MEDIUM;
    }

    return LeakSeverity.LOW;
  }
}
