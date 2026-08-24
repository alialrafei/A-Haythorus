package com.acorp.jvminsight.httpserver.constant;

public final class RouteConstants {

  private RouteConstants() {}

  public static final String ROOT = "/";
  public static final String UI = "/ui";
  public static final String SNAPSHOT = "/api/v1/snapshot";
  public static final String HISTORY = "/api/v1/history";
  public static final String CLUSTER = "/api/v1/cluster";
  public static final String JVMS = "/api/v1/jvms";

  public static final String MEMORY = "memory";
  public static final String MEMORY_POOLS = "memory-pools";
  public static final String GC = "gc";
  public static final String HISTOGRAM = "histogram";
  public static final String THREADS = "threads";
  public static final String THREAD_INFO = "thread-info";
  public static final String THREAD_COUNT = "thread-count";
  public static final String THREAD_CPU_TIMES = "thread-cpu-times";
  public static final String ANALYSIS = "analysis";
  public static final String DEADLOCKS = "deadlocks";
  public static final String TIMESTAMP = "timestamp";
  public static final String JVM_HISTORY = "history";
}
