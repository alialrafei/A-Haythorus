package com.acorp.jvminsight.snapshotcollection.dto;

import com.acorp.jvminsight.memory.GcSnapshot;
import com.acorp.jvminsight.memory.MemoryPoolSnapshot;
import com.acorp.jvminsight.memory.MemorySnapshot;
import com.acorp.jvminsight.memory.histogram.ClassHistogramEntry;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.thread.dto.ThreadDumpSnapshot;
import java.lang.management.ThreadInfo;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class JvmSnapshot {
  private long pid;
  private ThreadInfo[] threadsInfos;
  private MemorySnapshot memory;
  private List<GcSnapshot> gc;
  private List<MemoryPoolSnapshot> pools;
  private List<ClassHistogramEntry> histogram;
  private long timestamp;
  private JvmDeltaSnapshot delta;
  private long[] deadlocks;
  private long threadCount;
  private Map<Long, Long> threadCpuTimes;
  private ThreadDumpSnapshot dumpSnapshot;
}
