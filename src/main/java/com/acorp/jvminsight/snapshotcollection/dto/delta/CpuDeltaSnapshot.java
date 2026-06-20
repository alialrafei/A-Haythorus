package com.acorp.jvminsight.snapshotcollection.dto.delta;

import java.util.List;
import lombok.Data;

@Data
public class CpuDeltaSnapshot {

  private List<ThreadCpuDelta> topConsumers;
}
