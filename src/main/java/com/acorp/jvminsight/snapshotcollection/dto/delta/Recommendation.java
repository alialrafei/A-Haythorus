package com.acorp.jvminsight.snapshotcollection.dto.delta;

import java.util.List;
import lombok.Data;

@Data
public class Recommendation {
  private RecommendationSeverity severity;

  private double confidence;

  private String title;

  private String diagnosis;

  private String probableCause;

  private String recommendation;

  private List<String> evidence;
}
