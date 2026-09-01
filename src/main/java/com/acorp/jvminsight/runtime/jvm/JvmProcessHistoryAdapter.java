package com.acorp.jvminsight.runtime.jvm;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.ProcessHistorySample;

/**
 * JVM adapter that projects JVM snapshots into runtime-neutral process telemetry.
 *
 * <p>The generic analysis layer never depends on {@code JvmSnapshot}; only this adapter does.
 */
public final class JvmProcessHistoryAdapter {

  private JvmProcessHistoryAdapter() {}

  public static ProcessHistorySample from(JvmSnapshot snapshot) {
    long cpuTimeNanos =
        snapshot.getProcessCpu() == null ? 0L : snapshot.getProcessCpu().processCpuTimeNanos();

    int processors =
        snapshot.getProcessCpu() == null
            ? 0
            : Math.max(1, snapshot.getProcessCpu().availableProcessors());

    if (snapshot.getProcessIo() == null) {
      return new ProcessHistorySample(
          snapshot.getTimestamp(),
          cpuTimeNanos,
          processors,
          0L,
          0L,
          0L,
          0L,
          0L,
          0L);
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
