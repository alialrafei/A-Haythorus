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

  private static final long SAMPLE_INTERVAL_MS = 5000;

  private final long pid;
  private final MBeanServerConnection mbeanServer;

  /*
   * Keep the exact snapshot object last written by THIS collector.
   *
   * We use it during cleanup so an old collector cannot remove
   * a newer snapshot if the operating system reuses the same PID.
   */
  private JvmSnapshot lastStoredSnapshot;

  public JvmCollector(long pid) {

    this.pid = pid;

    LOGGER.info("Initializing collector for JVM pid={}", pid);

    this.mbeanServer = JvmAttachClient.attachAndGetMBeanServer(pid);

    LOGGER.info("Successfully attached to JVM pid={}", pid);
  }

  @Override
  public void run() {

    LOGGER.info("Starting collection loop for pid={}", pid);

    try {

      while (!Thread.currentThread().isInterrupted()) {

        /*
         * Check before touching JMX.
         *
         * If the process disappeared while we were sleeping,
         * terminate the collector immediately.
         */
        if (!isTargetJvmAlive()) {

          LOGGER.info("Target JVM pid={} is no longer alive. " + "Stopping collector.", pid);

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

            LOGGER.info("JMX connection lost because target JVM pid={} " + "has terminated.", pid);

            break;
          }

          LOGGER.warn(
              "JMX collection failure for live JVM pid={}. " + "Collector will retry.", pid, ex);

        } catch (Exception ex) {

          if (!isTargetJvmAlive()) {

            LOGGER.info("Collector failure occurred after JVM pid={} " + "terminated.", pid);

            break;
          }

          LOGGER.error(
              "Unexpected collector error for pid={}. " + "Collector will retry.", pid, ex);
        }

        /*
         * Sleep outside collectSnapshot().
         *
         * This also prevents a transient JMX error from causing
         * a tight retry loop that burns CPU.
         */
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

    collectHistogram(snapshot);

    snapshot.setTimestamp(Instant.now());

    /*
     * Read the previous snapshot before replacing it.
     */
    JvmSnapshot previousSnapshot = JvmDataStore.getSnapshot(pid);

    JvmDeltaSnapshot delta = DeltaEngine.compute(previousSnapshot, snapshot);

    snapshot.setDelta(delta);

    /*
     * Store only after the snapshot has been completely built.
     *
     * This prevents the HTTP API from observing a partially
     * constructed snapshot.
     */
    JvmDataStore.put(pid, snapshot);

    /*
     * Remember exactly what THIS collector inserted.
     */
    lastStoredSnapshot = snapshot;

    LOGGER.debug("Snapshot stored for pid={}", pid);
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

      /*
       * ThreadMXBean returns -1 if the thread no longer exists
       * or CPU accounting is unavailable.
       */
      if (cpuTime >= 0) {

        cpuTimes.put(id, cpuTime);
      }
    }

    snapshot.setThreadCpuTimes(cpuTimes);
  }

  private void collectMemory(JvmSnapshot snapshot) throws IOException {

    MemorySnapshot memory = MemoryCollector.collect(this.mbeanServer);

    snapshot.setMemory(memory);

    LOGGER.debug("Heap usage pid={} : {} MB / {} MB", pid, mb(memory.heapUsed), mb(memory.heapMax));
  }

  private void collectMemoryPools(JvmSnapshot snapshot) throws IOException {

    List<MemoryPoolSnapshot> pools = MemoryPoolCollector.collect(mbeanServer);

    snapshot.setPools(pools);

    LOGGER.debug("Collected {} memory pools for pid={}", pools.size(), pid);
  }

  private void collectGc(JvmSnapshot snapshot) throws IOException {

    List<GcSnapshot> gcs = GcCollector.collect(this.mbeanServer);

    snapshot.setGc(gcs);

    LOGGER.debug("Collected {} GC metrics for pid={}", gcs.size(), pid);
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

      LOGGER.info("Removed stale snapshot for terminated JVM pid={}", pid);

    } else {

      /*
       * This is actually GOOD.
       *
       * It means another collector has already written a newer
       * snapshot under the same PID, so this old collector must
       * not touch it.
       */
      LOGGER.debug(
          "Snapshot for pid={} was not removed because "
              + "the datastore now contains a different snapshot.",
          pid);
    }
  }

  private static long mb(long bytes) {

    return bytes <= 0 ? 0 : bytes / 1024 / 1024;
  }
}
