package com.acorp.jvminsight.memory;

public class MemorySnapshot {

  public final long timestamp;

  public final long heapUsed;
  public final long heapCommitted;
  public final long heapMax;

  public final long nonHeapUsed;
  public final long nonHeapCommitted;

  public long getTimestamp() {
    return timestamp;
  }

  public long getHeapUsed() {
    return heapUsed;
  }

  public long getHeapCommitted() {
    return heapCommitted;
  }

  public long getHeapMax() {
    return heapMax;
  }

  public long getNonHeapUsed() {
    return nonHeapUsed;
  }

  public long getNonHeapCommitted() {
    return nonHeapCommitted;
  }

  public MemorySnapshot(
      long heapUsed, long heapCommitted, long heapMax, long nonHeapUsed, long nonHeapCommitted) {
    this.timestamp = System.currentTimeMillis();
    this.heapUsed = heapUsed;
    this.heapCommitted = heapCommitted;
    this.heapMax = heapMax;
    this.nonHeapUsed = nonHeapUsed;
    this.nonHeapCommitted = nonHeapCommitted;
  }
}
