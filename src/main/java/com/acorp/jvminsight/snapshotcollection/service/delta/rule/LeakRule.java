package com.acorp.jvminsight.snapshotcollection.service.delta.rule;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.rule.context.RuleContext;

public interface LeakRule {
  void evaluate(
      JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta, RuleContext context);
}
