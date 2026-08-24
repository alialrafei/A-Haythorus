package com.acorp.jvminsight.system;

/** Cumulative Linux process I/O counters read from /proc/<pid>/io. */
public record ProcessIoSnapshot(
    long readCharacters,
    long writeCharacters,
    long readSyscalls,
    long writeSyscalls,
    long readBytes,
    long writeBytes,
    long cancelledWriteBytes) {}
