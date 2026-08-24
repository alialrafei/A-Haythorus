package com.acorp.jvminsight.snapshotcollection.dto.delta;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class JvmDeltaSnapshot {

  private Duration intervalMillis;

  private long previousHeapUsed;
  private long currentHeapUsed;
  private long heapDelta;
  private long positiveHeapDelta;
  private long reclaimedHeapBytes;
  private double heapGrowthPercentage;

  private long previousNonHeapUsed;
  private long currentNonHeapUsed;
  private long nonHeapDelta;
  private long positiveNonHeapDelta;
  private long reclaimedNonHeapBytes;
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

  /** Evidence score calculated from the current historical analysis window before smoothing. */
  private int instantaneousLeakScore;

  /** EWMA-smoothed leak confidence. This is the value exposed as the primary leak score. */
  private int leakScore;

  /** Fraction [0,1] of positive heap movements in the retained analysis window. */
  private double heapGrowthPersistence;

  /** Net heap growth across the retained analysis window in bytes. */
  private long windowHeapGrowthBytes;

  /** Number of GC collections observed across the retained analysis window. */
  private long windowGcCollections;

  /** Fraction [0,1] of the previous smoothed confidence preserved in the latest score. */
  private double historicalWeight;

  private List<String> leakReasons;
  private List<Recommendation> recommendations;
  private Map<Long, Long> threadCpuTimes;
}
