package com.acorp.jvminsight.memory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MemoryPoolSnapshot {

  public final String name;
  public final long used;
  public final long committed;
  public final long max;

  @JsonCreator
  public MemoryPoolSnapshot(
      @JsonProperty("name") String name,
      @JsonProperty("used") long used,
      @JsonProperty("committed") long committed,
      @JsonProperty("max") long max) {

    this.name = name;
    this.used = used;
    this.committed = committed;
    this.max = max;
  }
}
