package com.acorp.jvminsight.snapshotcollection.service;

import com.acorp.jvminsight.attach.JvmAttachClient;
import com.acorp.jvminsight.memory.GcCollector;
import com.acorp.jvminsight.memory.GcSnapshot;
import com.acorp.jvminsight.memory.MemoryCollector;
import com.acorp.jvminsight.memory.MemoryPoolCollector;
import com.acorp.jvminsight.memory.MemoryPoolSnapshot;
import com.acorp.jvminsight.memory.MemorySnapshot;
import com.acorp.jvminsight.memory.histogram.ClassHistogramEntry;
import com.acorp.jvminsight.memory.histogram.HistogramParser;
import com.acorp.jvminsight.snapshotcollection.JvmDataStore;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaEngine;
import com.acorp.jvminsight.thread.ThreadDumpParser;
import com.acorp.jvminsight.thread.ThreadDumpService;
import com.acorp.jvminsight.thread.dto.ThreadDumpSnapshot;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JvmCollector implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(JvmCollector.class);

  private static final long SAMPLE_INTERVAL_MS = 5000;

  private final long pid;
  private final MBeanServerConnection mbeanServer;

  public JvmCollector(long pid) {
    this.pid = pid;
    LOGGER.info("Initializing collector for JVM pid={}", pid);
    this.mbeanServer = JvmAttachClient.attachAndGetMBeanServer(pid);
    LOGGER.info("Successfully attached to JVM pid={}", pid);
  }

  @Override
  public void run() {
    LOGGER.info("Starting collection loop for pid={}", pid);
    while (true) {
      try {
        JvmSnapshot snapshot = new JvmSnapshot();
        try {
          ThreadDumpSnapshot threadDumpSnapshot =
              ThreadDumpParser.parse(ThreadDumpService.dumpAllThreads(mbeanServer));
          snapshot.setDumpSnapshot(threadDumpSnapshot);
        } catch (Exception ex) {
          LOGGER.error("Failed collecting thread dump for pid={}", pid, ex);
        }

        snapshot.setPid(pid);
        long[] deadlocks = null;
        try {
          deadlocks = ThreadDumpService.findDeadlockedThreads(mbeanServer);

        } catch (Exception ex) {
          LOGGER.error("Failed detecting deadlocks for pid={}", pid, ex);
        }
        snapshot.setDeadlocks(deadlocks);
        ThreadMXBean threadMXBean =
            ManagementFactory.newPlatformMXBeanProxy(
                mbeanServer, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);
        threadMXBean.setThreadCpuTimeEnabled(true);
        Map<Long, Long> cpuTimes = new HashMap<>();

        for (long id : threadMXBean.getAllThreadIds()) {
          cpuTimes.put(id, threadMXBean.getThreadCpuTime(id));
        }

        snapshot.setThreadCpuTimes(cpuTimes);
        if (deadlocks != null && deadlocks.length > 0) {
          LOGGER.warn("Detected {} deadlocked thread(s) in pid={}", deadlocks.length, pid);
          ThreadInfo[] infos = threadMXBean.getThreadInfo(deadlocks, true, true);
          snapshot.setThreadCount(threadMXBean.getThreadCount());
          snapshot.setThreadsInfos(infos);
        }
        snapshot.setThreadCount(threadMXBean.getThreadCount());
        MemorySnapshot mem = MemoryCollector.collect(mbeanServer);

        snapshot.setMemory(mem);
        LOGGER.debug("Heap usage pid={} : {} MB / {} MB", pid, mb(mem.heapUsed), mb(mem.heapMax));

        List<MemoryPoolSnapshot> pools = MemoryPoolCollector.collect(mbeanServer);

        snapshot.setPools(pools);
        LOGGER.debug("Collected {} memory pools for pid={}", pools.size(), pid);
        List<GcSnapshot> gcs = GcCollector.collect(mbeanServer);
        snapshot.setGc(gcs);
        LOGGER.debug("Collected {} GC metrics for pid={}", gcs.size(), pid);
        ObjectName dcmd = new ObjectName("com.sun.management:type=DiagnosticCommand");
        String histogram =
            (String)
                mbeanServer.invoke(
                    dcmd,
                    "gcClassHistogram",
                    new Object[] {new String[] {"-all"}},
                    new String[] {"[Ljava.lang.String;"});
        List<ClassHistogramEntry> classesHistogram = HistogramParser.parse(histogram);
        classesHistogram =
            HistogramParser.sortByBytesDesc(classesHistogram).stream().limit(50).toList();
        snapshot.setHistogram(classesHistogram);
        LOGGER.debug(
            "Collected histogram with {} classes for pid={}", classesHistogram.size(), pid);
        snapshot.setTimestamp(System.currentTimeMillis());
        JvmDeltaSnapshot jvmDeltaSnapshot =
            DeltaEngine.compute(JvmDataStore.getSnapshot(pid), snapshot);
        snapshot.setDelta(jvmDeltaSnapshot);
        JvmDataStore.put(pid, snapshot);
        LOGGER.debug("Snapshot stored for pid={}", pid);
        Thread.sleep(SAMPLE_INTERVAL_MS);
      } catch (InterruptedException ex) {
        LOGGER.warn("Collector interrupted for pid={}", pid);
        Thread.currentThread().interrupt();
        break;
      } catch (MalformedObjectNameException
          | InstanceNotFoundException
          | MBeanException
          | ReflectionException
          | IOException ex) {

        LOGGER.error("JMX collection failure for pid={}", pid, ex);
      } catch (Exception ex) {
        LOGGER.error("Unexpected collector error for pid={}", pid, ex);
      }
    }
    LOGGER.info("Collector terminated for pid={}", pid);
  }

  private static long mb(long bytes) {

    return bytes <= 0 ? 0 : bytes / 1024 / 1024;
  }
}
