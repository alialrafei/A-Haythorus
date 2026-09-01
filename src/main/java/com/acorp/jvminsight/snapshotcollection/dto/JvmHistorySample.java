package com.acorp.jvminsight.snapshotcollection.dto;

import com.acorp.jvminsight.memory.GcSnapshot;
import com.acorp.jvminsight.runtime.jvm.JvmProcessHistoryAdapter;
import com.acorp.jvminsight.memory.MemoryPoolSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight JVM history.
 *
 * <p>Runtime-neutral process counters live in {@link ProcessHistorySample}. JVM-only memory, GC,
 * and thread state remain here.
 */
public record JvmHistorySample(
    Instant timestamp,
    long heapUsed,
    long nonHeapUsed,
    long oldGenerationUsed,
    long threadCount,
    Map<String, Long> gcCollectionCounts,
    ProcessHistorySample process,
    long processCpuTimeNanos,
    long processReadBytes,
    long processWriteBytes,
    double leakConfidence) {

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

    ProcessHistorySample process = JvmProcessHistoryAdapter.from(snapshot);
    double confidence = snapshot.getDelta() == null ? 0.0 : snapshot.getDelta().getLeakScore();

    return new JvmHistorySample(
        snapshot.getTimestamp(),
        heap,
        nonHeap,
        oldGen,
        snapshot.getThreadCount(),
        Map.copyOf(gcCounts),
        process,
        process.cpuTimeNanos(),
        process.readBytes(),
        process.writeBytes(),
        confidence);
  }
}
