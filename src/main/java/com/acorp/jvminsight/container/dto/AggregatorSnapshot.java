package com.acorp.jvminsight.container.dto;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import java.util.List;

public class AggregatorSnapshot {
  public PodInfo pod;
  public long time;
  public List<JvmSnapshot> jvmSnapshots;

  public AggregatorSnapshot() {}

  public void setPod(PodInfo pod) {
    this.pod = pod;
  }

  public void setTime(long time) {
    this.time = time;
  }

  public void setJvmSnapshots(List<JvmSnapshot> jvmSnapshots) {
    this.jvmSnapshots = jvmSnapshots;
  }

  public PodInfo getPod() {
    return pod;
  }

  public long getTime() {
    return time;
  }

  public List<JvmSnapshot> getJvmSnapshots() {
    return jvmSnapshots;
  }
}
