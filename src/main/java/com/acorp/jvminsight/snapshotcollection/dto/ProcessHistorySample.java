package com.acorp.jvminsight.snapshotcollection.dto;

import java.time.Instant;

/**
 * Runtime-neutral process history used by operating-system analyzers.
 *
 * <p>This record contains no JVM-specific concepts or dependencies. Any runtime adapter that can
 * provide process CPU and Linux I/O counters can feed the same CPU and I/O analyzers.
 */
public record ProcessHistorySample(
    Instant timestamp,
    long cpuTimeNanos,
    int availableProcessors,
    long readCharacters,
    long writeCharacters,
    long readSyscalls,
    long writeSyscalls,
    long readBytes,
    long writeBytes) {}
