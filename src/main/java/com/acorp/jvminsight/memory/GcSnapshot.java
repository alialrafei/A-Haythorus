package com.acorp.jvminsight.memory;

public class GcSnapshot {

  public final String name;
  public final long collectionCount;
  public final long collectionTimeMillis;

  public GcSnapshot(String name, long count, long timeMillis) {
    this.name = name;
    this.collectionCount = count;
    this.collectionTimeMillis = timeMillis;
  }
}
