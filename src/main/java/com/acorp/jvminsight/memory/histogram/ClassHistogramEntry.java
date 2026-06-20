package com.acorp.jvminsight.memory.histogram;

public class ClassHistogramEntry {
  private String className;
  private long instances;
  private long bytes;

  public ClassHistogramEntry() {}

  public ClassHistogramEntry(String className, long instances, long bytes) {
    this.className = className;
    this.instances = instances;
    this.bytes = bytes;
  }

  public String getClassName() {
    return className;
  }

  public long getInstances() {
    return instances;
  }

  public long getBytes() {
    return bytes;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public void setInstances(long instances) {
    this.instances = instances;
  }

  public void setBytes(long bytes) {
    this.bytes = bytes;
  }
}
