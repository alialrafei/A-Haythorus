package com.acorp.jvminsight.container.dto;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import java.time.Instant;
import java.util.List;

public class AggregatorSnapshot {
  public PodInfo pod;
  public Instant time;
  public List<JvmSnapshot> jvmSnapshots;

  public AggregatorSnapshot() {}

  public void setPod(PodInfo pod) {
    this.pod = pod;
  }

  public void setTime(Instant time) {
    this.time = time;
  }

  public void setJvmSnapshots(List<JvmSnapshot> jvmSnapshots) {
    this.jvmSnapshots = jvmSnapshots;
  }

  public PodInfo getPod() {
    return pod;
  }

  public Instant getTime() {
    return time;
  }

  public List<JvmSnapshot> getJvmSnapshots() {
    return jvmSnapshots;
  }
}
