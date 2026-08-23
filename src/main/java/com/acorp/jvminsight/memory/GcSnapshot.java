package com.acorp.jvminsight.memory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GcSnapshot {

  public final String name;
  public final long collectionCount;
  public final long collectionTimeMillis;
  
  @JsonCreator
  public GcSnapshot(
      @JsonProperty("name") String name,
      @JsonProperty("collectionCount") long collectionCount,
      @JsonProperty("collectionTimeMillis") long collectionTimeMillis) {
    this.name = name;
    this.collectionCount = collectionCount;
    this.collectionTimeMillis = collectionTimeMillis;
  }
}
