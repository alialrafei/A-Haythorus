package com.acorp.jvminsight.snapshotcollection.service.delta.strategy;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.CpuDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.ThreadCpuDelta;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaComputationStrategy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CpuDeltaStrategy implements DeltaComputationStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(CpuDeltaStrategy.class);
  private static final int TOP_THREADS = ConfigLoader.getInt("analysis.cpu.top-threads", 10);

  @Override
  public void compute(JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta) {
    CpuDeltaSnapshot snapshot = new CpuDeltaSnapshot();

    computeProcessCpu(previous, current, snapshot);
    computeThreadCpu(previous, current, snapshot);

    delta.setCpuDelta(snapshot);
  }

  private void computeProcessCpu(
      JvmSnapshot previous, JvmSnapshot current, CpuDeltaSnapshot snapshot) {
    if (previous.getProcessCpu() == null || current.getProcessCpu() == null) {
      return;
    }

    long cpuDelta =
        current.getProcessCpu().processCpuTimeNanos()
            - previous.getProcessCpu().processCpuTimeNanos();
    cpuDelta = Math.max(0L, cpuDelta);

    long intervalNanos = 0L;
    if (previous.getTimestamp() != null && current.getTimestamp() != null) {
      intervalNanos = Duration.between(previous.getTimestamp(), current.getTimestamp()).toNanos();
    }

    int processors = Math.max(1, current.getProcessCpu().availableProcessors());
    double utilization =
        intervalNanos <= 0 ? 0.0 : (cpuDelta / (double) (intervalNanos * processors)) * 100.0;

    snapshot.setProcessCpuTimeDeltaNanos(cpuDelta);
    snapshot.setProcessCpuUtilizationPercentage(Math.max(0.0, Math.min(100.0, utilization)));
    snapshot.setProcessCpuLoad(current.getProcessCpu().processCpuLoad());
    snapshot.setSystemCpuLoad(current.getProcessCpu().systemCpuLoad());
    snapshot.setAvailableProcessors(processors);
  }

  private void computeThreadCpu(
      JvmSnapshot previous, JvmSnapshot current, CpuDeltaSnapshot snapshot) {
    if (previous.getThreadCpuTimes() == null || current.getThreadCpuTimes() == null) {
      snapshot.setTopConsumers(List.of());
      return;
    }

    Map<Long, Long> previousCpu = previous.getThreadCpuTimes();
    List<ThreadCpuDelta> topConsumers = new ArrayList<>();

    for (Map.Entry<Long, Long> entry : current.getThreadCpuTimes().entrySet()) {
      Long previousValue = previousCpu.get(entry.getKey());
      if (previousValue == null) {
        continue;
      }

      long deltaNanos = entry.getValue() - previousValue;
      if (deltaNanos <= 0) {
        continue;
      }

      ThreadCpuDelta cpuDelta = new ThreadCpuDelta();
      cpuDelta.setThreadId(entry.getKey());
      cpuDelta.setCpuTimeDeltaNanos(deltaNanos);
      topConsumers.add(cpuDelta);
    }

    topConsumers.sort(Comparator.comparingLong(ThreadCpuDelta::getCpuTimeDeltaNanos).reversed());
    if (topConsumers.size() > TOP_THREADS) {
      topConsumers = new ArrayList<>(topConsumers.subList(0, TOP_THREADS));
    }

    snapshot.setTopConsumers(topConsumers);
    LOGGER.debug("Computed CPU delta for {} threads.", topConsumers.size());
  }
}
