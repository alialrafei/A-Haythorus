package com.acorp.jvminsight.system;

/** Lightweight process/system CPU counters collected from OperatingSystemMXBean. */
public record ProcessCpuSnapshot(
    long processCpuTimeNanos,
    double processCpuLoad,
    double systemCpuLoad,
    int availableProcessors) {}
