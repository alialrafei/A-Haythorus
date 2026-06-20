package com.acorp.jvminsight.snapshotcollection.service.delta.rule.context;

import com.acorp.jvminsight.snapshotcollection.dto.delta.Recommendation;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RuleContext {

  private int score;

  private final List<String> reasons = new ArrayList<>();

  private final List<Recommendation> recommendations = new ArrayList<>();

  public void addScore(int points) {
    score += points;
  }
}
