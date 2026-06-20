package com.acorp.jvminsight.snapshotcollection.dto.delta;

import lombok.Data;

@Data
public class MemoryPoolDelta {
  private String poolName;

  private long previousUsed;
  private long currentUsed;
  private long usedDelta;
  private double usedGrowthPercentage;

  private long previousCommitted;
  private long currentCommitted;
  private long committedDelta;
  private double committedGrowthPercentage;

  private long previousMax;
  private long currentMax;
  private long maxDelta;
}
