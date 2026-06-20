package com.acorp.jvminsight.snapshotcollection.dto.delta;

import lombok.Data;

@Data
public class GcDeltaSnapshot {
  private String gcName;

  private long previousCollectionCount;

  private long currentCollectionCount;

  private long collectionCountDelta;

  private double collectionCountGrowthPercentage;

  private long previousCollectionTimeMillis;

  private long currentCollectionTimeMillis;

  private long collectionTimeDeltaMillis;

  private double collectionTimeGrowthPercentage;
}
