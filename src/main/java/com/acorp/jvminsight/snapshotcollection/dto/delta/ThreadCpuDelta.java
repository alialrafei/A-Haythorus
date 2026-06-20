package com.acorp.jvminsight.snapshotcollection.dto.delta;

import lombok.Data;

@Data
public class ThreadCpuDelta {
  private long threadId;

  private String threadName;

  private long cpuTimeDeltaNanos;
}
