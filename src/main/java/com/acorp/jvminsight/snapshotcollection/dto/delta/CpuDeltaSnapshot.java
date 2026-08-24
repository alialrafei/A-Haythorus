package com.acorp.jvminsight.snapshotcollection.dto.delta;

import java.util.List;
import lombok.Data;

@Data
public class CpuDeltaSnapshot {

  private long processCpuTimeDeltaNanos;
  private double processCpuUtilizationPercentage;
  private double processCpuLoad;
  private double systemCpuLoad;
  private int availableProcessors;
  private List<ThreadCpuDelta> topConsumers;
}
