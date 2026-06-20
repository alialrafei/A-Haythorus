package com.acorp.jvminsight.memory;

import lombok.Data;

@Data
public class MemoryPoolSnapshot {

  public final String name;
  public final long used;
  public final long committed;
  public final long max;

  public MemoryPoolSnapshot(String name, long used, long committed, long max) {
    this.name = name;
    this.used = used;
    this.committed = committed;
    this.max = max;
  }
}
