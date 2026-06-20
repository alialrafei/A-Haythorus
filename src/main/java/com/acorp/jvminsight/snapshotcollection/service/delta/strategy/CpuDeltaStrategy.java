package com.acorp.jvminsight.snapshotcollection.service.delta.strategy;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.CpuDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.ThreadCpuDelta;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaComputationStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CpuDeltaStrategy implements DeltaComputationStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(CpuDeltaStrategy.class);

  private static final int TOP_THREADS = 10;

  @Override
  public void compute(JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta) {

    if (previous.getThreadCpuTimes() == null || current.getThreadCpuTimes() == null) {

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

      topConsumers = topConsumers.subList(0, TOP_THREADS);
    }
    CpuDeltaSnapshot snapshot = new CpuDeltaSnapshot();

    snapshot.setTopConsumers(topConsumers);

    delta.setCpuDelta(snapshot);

    LOGGER.debug("Computed CPU delta for {} threads.", topConsumers.size());
  }
}
