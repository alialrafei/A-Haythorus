package com.acorp.jvminsight.snapshotcollection.dto.delta;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class JvmDeltaSnapshot {

  private long intervalMillis;

  private long previousHeapUsed;

  private long currentHeapUsed;

  private long heapDelta;

  private double heapGrowthPercentage;

  private long previousNonHeapUsed;

  private long currentNonHeapUsed;

  private long nonHeapDelta;

  private double nonHeapGrowthPercentage;

  private long previousThreadCount;

  private long currentThreadCount;

  private long threadDelta;

  private double threadGrowthPercentage;

  private List<GcDeltaSnapshot> gcDelta;

  private List<HistogramDelta> histogramDelta;

  private List<MemoryPoolDelta> poolDelta;

  private int currentDeadlockCount;

  private int deadlockDelta;

  private LeakSeverity leakSeverity;

  private CpuDeltaSnapshot cpuDelta;

  private int leakScore;

  private List<String> leakReasons;

  private List<Recommendation> recommendations;

  private Map<Long, Long> threadCpuTimes;
}
