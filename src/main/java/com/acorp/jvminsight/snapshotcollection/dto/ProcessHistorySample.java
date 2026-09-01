package com.acorp.jvminsight.snapshotcollection.dto;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import java.time.Instant;

/**
 * Runtime-neutral process history used by operating-system analyzers.
 *
 * <p>This DTO intentionally contains no JVM-specific concepts. Any runtime adapter that can provide
 * process CPU and Linux I/O counters can feed the same CPU and I/O analyzers.
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
    long writeBytes) {

  public static ProcessHistorySample from(JvmSnapshot snapshot) {
    long cpuTimeNanos =
        snapshot.getProcessCpu() == null ? 0L : snapshot.getProcessCpu().processCpuTimeNanos();
    int processors =
        snapshot.getProcessCpu() == null
            ? 0
            : Math.max(1, snapshot.getProcessCpu().availableProcessors());

    if (snapshot.getProcessIo() == null) {
      return new ProcessHistorySample(
          snapshot.getTimestamp(), cpuTimeNanos, processors, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    return new ProcessHistorySample(
        snapshot.getTimestamp(),
        cpuTimeNanos,
        processors,
        snapshot.getProcessIo().readCharacters(),
        snapshot.getProcessIo().writeCharacters(),
        snapshot.getProcessIo().readSyscalls(),
        snapshot.getProcessIo().writeSyscalls(),
        snapshot.getProcessIo().readBytes(),
        snapshot.getProcessIo().writeBytes());
  }
}
