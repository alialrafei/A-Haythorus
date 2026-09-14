package com.acorp.jvminsight.system;

/**
 * Linux process-level memory measurements that are outside the JVM heap/non-heap model.
 * Values are bytes and may be unavailable when the container cannot read /proc for the target.
 */
public record ProcessMemorySnapshot(
    long residentBytes,
    long anonymousResidentBytes,
    long fileResidentBytes,
    long sharedResidentBytes,
    long dataBytes,
    long mainThreadStackBytes,
    long allThreadStacksBytes,
    long swapBytes,
    long directBufferBytes,
    long mappedBufferBytes) {}
