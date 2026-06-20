package com.acorp.jvminsight.snapshotcollection.dto.delta;

import lombok.Data;

@Data
public class HistogramDelta {

  private String className;

  private long previousBytes;

  private long currentBytes;

  private long bytesDelta;

  private long previousInstances;

  private long currentInstances;

  private long instancesDelta;

  public HistogramDelta() {}
}
