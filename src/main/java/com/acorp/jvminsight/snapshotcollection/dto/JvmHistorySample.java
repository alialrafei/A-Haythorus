package com.acorp.jvminsight.snapshotcollection.dto;

import com.acorp.jvminsight.memory.GcSnapshot;
import com.acorp.jvminsight.memory.MemoryPoolSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight historical sample used for trend analysis without retaining heavy thread dumps or
 * histograms.
 */
public record JvmHistorySample(
    Instant timestamp,
    long heapUsed,
    long nonHeapUsed,
    long oldGenerationUsed,
    long threadCount,
    Map<String, Long> gcCollectionCounts,
    long processCpuTimeNanos,
    long processReadBytes,
    long processWriteBytes,
    int leakConfidence) {

  public static JvmHistorySample from(JvmSnapshot snapshot) {
    long heap = snapshot.getMemory() == null ? 0 : snapshot.getMemory().heapUsed;
    long nonHeap = snapshot.getMemory() == null ? 0 : snapshot.getMemory().nonHeapUsed;

    long oldGen = 0;
    if (snapshot.getPools() != null) {
      for (MemoryPoolSnapshot pool : snapshot.getPools()) {
        String name = pool.name == null ? "" : pool.name.toLowerCase();
        if (name.contains("old gen")
            || name.contains("tenured")
            || name.contains("old generation")) {
          oldGen = pool.used;
          break;
        }
      }
    }

    Map<String, Long> gcCounts = new HashMap<>();
    if (snapshot.getGc() != null) {
      for (GcSnapshot gc : snapshot.getGc()) {
        gcCounts.put(gc.name, gc.collectionCount);
      }
    }

    long processCpuTimeNanos =
        snapshot.getProcessCpu() == null ? 0 : snapshot.getProcessCpu().processCpuTimeNanos();
    long readBytes = snapshot.getProcessIo() == null ? 0 : snapshot.getProcessIo().readBytes();
    long writeBytes = snapshot.getProcessIo() == null ? 0 : snapshot.getProcessIo().writeBytes();
    int confidence = snapshot.getDelta() == null ? 0 : snapshot.getDelta().getLeakScore();

    return new JvmHistorySample(
        snapshot.getTimestamp(),
        heap,
        nonHeap,
        oldGen,
        snapshot.getThreadCount(),
        Map.copyOf(gcCounts),
        processCpuTimeNanos,
        readBytes,
        writeBytes,
        confidence);
  }
}
