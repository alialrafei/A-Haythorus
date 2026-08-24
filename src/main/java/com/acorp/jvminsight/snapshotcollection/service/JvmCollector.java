package com.acorp.jvminsight.snapshotcollection.service;

import com.acorp.jvminsight.attach.JvmAttachClient;
import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.memory.GcCollector;
import com.acorp.jvminsight.memory.GcSnapshot;
import com.acorp.jvminsight.memory.MemoryCollector;
import com.acorp.jvminsight.memory.MemoryPoolCollector;
import com.acorp.jvminsight.memory.MemoryPoolSnapshot;
import com.acorp.jvminsight.memory.MemorySnapshot;
import com.acorp.jvminsight.memory.histogram.ClassHistogramEntry;
import com.acorp.jvminsight.memory.histogram.HistogramParser;
import com.acorp.jvminsight.snapshotcollection.JvmDataStore;
import com.acorp.jvminsight.snapshotcollection.dto.JvmHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaEngine;
import com.acorp.jvminsight.system.ProcessCpuSnapshot;
import com.acorp.jvminsight.system.ProcessIoCollector;
import com.acorp.jvminsight.thread.ThreadDumpParser;
import com.acorp.jvminsight.thread.ThreadDumpService;
import com.acorp.jvminsight.thread.dto.ThreadDumpSnapshot;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
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
  private static final long SAMPLE_INTERVAL_MS =
      Math.max(250L, ConfigLoader.getLong("collector.interval.ms", 5000L));

  private final long pid;
  private final MBeanServerConnection mbeanServer;
  private JvmSnapshot lastStoredSnapshot;

  public JvmCollector(long pid) {
    this.pid = pid;
    LOGGER.info("Initializing collector for JVM pid={}", pid);
    this.mbeanServer = JvmAttachClient.attachAndGetMBeanServer(pid);
    LOGGER.info("Successfully attached to JVM pid={}", pid);
  }

  @Override
  public void run() {
    LOGGER.info("Starting collection loop for pid={} intervalMs={}", pid, SAMPLE_INTERVAL_MS);

    try {
      while (!Thread.currentThread().isInterrupted()) {
        if (!isTargetJvmAlive()) {
          LOGGER.info("Target JVM pid={} is no longer alive. Stopping collector.", pid);
          break;
        }

        try {
          collectSnapshot();
        } catch (InterruptedException ex) {
          LOGGER.info("Collector interrupted for pid={}", pid);
          Thread.currentThread().interrupt();
          break;
        } catch (MalformedObjectNameException
            | InstanceNotFoundException
            | MBeanException
            | ReflectionException
            | IOException ex) {
          if (!isTargetJvmAlive()) {
            LOGGER.info("JMX connection lost because target JVM pid={} has terminated.", pid);
            break;
          }
          LOGGER.warn("JMX collection failure for live JVM pid={}. Collector will retry.", pid, ex);
        } catch (Exception ex) {
          if (!isTargetJvmAlive()) {
            LOGGER.info("Collector failure occurred after JVM pid={} terminated.", pid);
            break;
          }
          LOGGER.error("Unexpected collector error for pid={}. Collector will retry.", pid, ex);
        }

        try {
          Thread.sleep(SAMPLE_INTERVAL_MS);
        } catch (InterruptedException ex) {
          LOGGER.info("Collector interrupted while waiting for pid={}", pid);
          Thread.currentThread().interrupt();
          break;
        }
      }
    } finally {
      cleanupSnapshot();
      LOGGER.info("Collector terminated for pid={}", pid);
    }
  }

  private void collectSnapshot() throws Exception {
    JvmSnapshot snapshot = new JvmSnapshot();
    snapshot.setPid(pid);

    collectThreadDump(snapshot);

    ThreadMXBean threadMXBean = createThreadMxBean();
    collectDeadlocks(snapshot, threadMXBean);
    collectThreadCpuTimes(snapshot, threadMXBean);
    snapshot.setThreadCount(threadMXBean.getThreadCount());

    collectMemory(snapshot);
    collectMemoryPools(snapshot);
    collectGc(snapshot);
    collectProcessCpu(snapshot);
    collectProcessIo(snapshot);
    collectHistogram(snapshot);

    snapshot.setTimestamp(Instant.now());

    JvmSnapshot previousSnapshot = JvmDataStore.getSnapshot(pid);
    List<JvmHistorySample> retainedHistory = JvmDataStore.getHistory(pid);

    JvmDeltaSnapshot delta = DeltaEngine.compute(retainedHistory, previousSnapshot, snapshot);
    snapshot.setDelta(delta);

    JvmDataStore.put(pid, snapshot);
    lastStoredSnapshot = snapshot;

    LOGGER.debug(
        "Snapshot stored for pid={} historySamples={} leakEvidence={} leakConfidence={}",
        pid,
        retainedHistory.size() + 1,
        delta.getInstantaneousLeakScore(),
        delta.getLeakScore());
  }

  private void collectThreadDump(JvmSnapshot snapshot) {
    try {
      ThreadDumpSnapshot threadDumpSnapshot =
          ThreadDumpParser.parse(ThreadDumpService.dumpAllThreads(mbeanServer));
      snapshot.setDumpSnapshot(threadDumpSnapshot);
    } catch (Exception ex) {
      LOGGER.warn("Failed collecting thread dump for pid={}", pid, ex);
    }
  }

  private ThreadMXBean createThreadMxBean() throws IOException {
    ThreadMXBean threadMXBean =
        ManagementFactory.newPlatformMXBeanProxy(
            mbeanServer, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);

    if (threadMXBean.isThreadCpuTimeSupported() && !threadMXBean.isThreadCpuTimeEnabled()) {
      threadMXBean.setThreadCpuTimeEnabled(true);
    }
    return threadMXBean;
  }

  private void collectDeadlocks(JvmSnapshot snapshot, ThreadMXBean threadMXBean) {
    try {
      long[] deadlocks = ThreadDumpService.findDeadlockedThreads(mbeanServer);
      snapshot.setDeadlocks(deadlocks);

      if (deadlocks == null || deadlocks.length == 0) {
        return;
      }

      LOGGER.warn("Detected {} deadlocked thread(s) in pid={}", deadlocks.length, pid);
      ThreadInfo[] infos = threadMXBean.getThreadInfo(deadlocks, true, true);
      snapshot.setThreadsInfos(infos);
    } catch (Exception ex) {
      LOGGER.warn("Failed detecting deadlocks for pid={}", pid, ex);
    }
  }

  private void collectThreadCpuTimes(JvmSnapshot snapshot, ThreadMXBean threadMXBean) {
    Map<Long, Long> cpuTimes = new HashMap<>();

    if (!threadMXBean.isThreadCpuTimeSupported()) {
      snapshot.setThreadCpuTimes(cpuTimes);
      return;
    }

    for (long id : threadMXBean.getAllThreadIds()) {
      long cpuTime = threadMXBean.getThreadCpuTime(id);
      if (cpuTime >= 0) {
        cpuTimes.put(id, cpuTime);
      }
    }
    snapshot.setThreadCpuTimes(cpuTimes);
  }

  private void collectMemory(JvmSnapshot snapshot) throws Exception {
    MemorySnapshot memory = MemoryCollector.collect(this.mbeanServer);
    snapshot.setMemory(memory);
    LOGGER.debug("Heap usage pid={} : {} MB / {} MB", pid, mb(memory.heapUsed), mb(memory.heapMax));
  }

  private void collectMemoryPools(JvmSnapshot snapshot) throws Exception {
    List<MemoryPoolSnapshot> pools = MemoryPoolCollector.collect(mbeanServer);
    snapshot.setPools(pools);
    LOGGER.debug("Collected {} memory pools for pid={}", pools.size(), pid);
  }

  private void collectGc(JvmSnapshot snapshot) throws Exception {
    List<GcSnapshot> gcs = GcCollector.collect(this.mbeanServer);
    snapshot.setGc(gcs);
    LOGGER.debug("Collected {} GC metrics for pid={}", gcs.size(), pid);
  }

  private void collectProcessCpu(JvmSnapshot snapshot) {
    try {
      com.sun.management.OperatingSystemMXBean osBean =
          ManagementFactory.newPlatformMXBeanProxy(
              mbeanServer,
              ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
              com.sun.management.OperatingSystemMXBean.class);

      snapshot.setProcessCpu(
          new ProcessCpuSnapshot(
              osBean.getProcessCpuTime(),
              osBean.getProcessCpuLoad(),
              osBean.getCpuLoad(),
              osBean.getAvailableProcessors()));
    } catch (Exception ex) {
      LOGGER.warn("Failed collecting process CPU metrics for pid={}", pid, ex);
    }
  }

  private void collectProcessIo(JvmSnapshot snapshot) {
    try {
      snapshot.setProcessIo(ProcessIoCollector.collect(pid));
    } catch (IOException | SecurityException ex) {
      LOGGER.warn("Failed collecting /proc process IO metrics for pid={}", pid, ex);
    }
  }

  private void collectHistogram(JvmSnapshot snapshot)
      throws MalformedObjectNameException,
          InstanceNotFoundException,
          MBeanException,
          ReflectionException,
          IOException {

    ObjectName diagnosticCommand = new ObjectName("com.sun.management:type=DiagnosticCommand");

    String histogram =
        (String)
            mbeanServer.invoke(
                diagnosticCommand,
                "gcClassHistogram",
                new Object[] {new String[] {"-all"}},
                new String[] {"[Ljava.lang.String;"});

    List<ClassHistogramEntry> classes = HistogramParser.parse(histogram);
    classes = HistogramParser.sortByBytesDesc(classes).stream().toList();
    snapshot.setHistogram(classes);

    LOGGER.debug("Collected histogram with {} classes for pid={}", classes.size(), pid);
  }

  private boolean isTargetJvmAlive() {
    return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
  }

  private void cleanupSnapshot() {
    if (lastStoredSnapshot == null) {
      return;
    }

    boolean removed = JvmDataStore.remove(pid, lastStoredSnapshot);
    if (removed) {
      LOGGER.info("Removed stale snapshot and history for terminated JVM pid={}", pid);
    } else {
      LOGGER.debug(
          "Snapshot for pid={} was not removed because the datastore now contains a different snapshot.",
          pid);
    }
  }

  private static long mb(long bytes) {
    return bytes <= 0 ? 0 : bytes / 1024 / 1024;
  }
}
