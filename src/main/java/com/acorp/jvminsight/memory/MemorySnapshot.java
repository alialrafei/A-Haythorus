package com.acorp.jvminsight.memory;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MemorySnapshot {

  public final Instant timestamp;

  public final long heapUsed;
  public final long heapCommitted;
  public final long heapMax;

  public final long nonHeapUsed;
  public final long nonHeapCommitted;

  @JsonCreator
  public MemorySnapshot(
      @JsonProperty("timestamp") Instant timestamp,
      @JsonProperty("heapUsed") long heapUsed,
      @JsonProperty("heapCommitted") long heapCommitted,
      @JsonProperty("heapMax") long heapMax,
      @JsonProperty("nonHeapUsed") long nonHeapUsed,
      @JsonProperty("nonHeapCommitted") long nonHeapCommitted) {

    this.timestamp = timestamp;
    this.heapUsed = heapUsed;
    this.heapCommitted = heapCommitted;
    this.heapMax = heapMax;
    this.nonHeapUsed = nonHeapUsed;
    this.nonHeapCommitted = nonHeapCommitted;
  }
  public Instant getTimestamp() {
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
    this.timestamp = Instant.now();
    this.heapUsed = heapUsed;
    this.heapCommitted = heapCommitted;
    this.heapMax = heapMax;
    this.nonHeapUsed = nonHeapUsed;
    this.nonHeapCommitted = nonHeapCommitted;
  }
}
