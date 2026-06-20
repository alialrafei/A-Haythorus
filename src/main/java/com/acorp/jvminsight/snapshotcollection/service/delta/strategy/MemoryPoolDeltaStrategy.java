package com.acorp.jvminsight.snapshotcollection.service.delta.strategy;

import com.acorp.jvminsight.memory.MemoryPoolSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.MemoryPoolDelta;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaComputationStrategy;
import com.acorp.jvminsight.util.GrowthCalculator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes memory pool growth between two snapshots.
 *
 * <p>Pools are matched by pool name.
 *
 * <p>Computes: - previous values - current values - deltas - growth percentages
 *
 * <p>This implementation is stateless and thread-safe.
 */
public final class MemoryPoolDeltaStrategy implements DeltaComputationStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(MemoryPoolDeltaStrategy.class);

  @Override
  public void compute(JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta) {

    if (previous.getPools() == null || current.getPools() == null) {

      LOGGER.debug("Skipping memory pool delta computation.");

      return;
    }

    Map<String, MemoryPoolSnapshot> previousMap = toMap(previous.getPools());

    List<MemoryPoolDelta> deltas = new ArrayList<>();

    for (MemoryPoolSnapshot currentPool : current.getPools()) {

      MemoryPoolSnapshot previousPool = previousMap.get(currentPool.name);

      if (previousPool == null) {
        continue;
      }

      MemoryPoolDelta poolDelta = new MemoryPoolDelta();

      poolDelta.setPoolName(currentPool.name);

      // Used

      poolDelta.setPreviousUsed(previousPool.used);

      poolDelta.setCurrentUsed(currentPool.used);

      poolDelta.setUsedDelta(currentPool.used - previousPool.used);

      poolDelta.setUsedGrowthPercentage(
          GrowthCalculator.percentageGrowth(previousPool.used, currentPool.used));

      // Committed

      poolDelta.setPreviousCommitted(previousPool.committed);

      poolDelta.setCurrentCommitted(currentPool.committed);

      poolDelta.setCommittedDelta(currentPool.committed - previousPool.committed);

      poolDelta.setCommittedGrowthPercentage(
          GrowthCalculator.percentageGrowth(previousPool.committed, currentPool.committed));

      // Max

      poolDelta.setPreviousMax(previousPool.max);

      poolDelta.setCurrentMax(currentPool.max);

      poolDelta.setMaxDelta(currentPool.max - previousPool.max);

      deltas.add(poolDelta);

      LOGGER.trace(
          "Pool={} usedGrowth={}%",
          currentPool.name, String.format("%.2f", poolDelta.getUsedGrowthPercentage()));
    }

    delta.setPoolDelta(deltas);

    LOGGER.debug("Computed {} memory pool deltas.", deltas.size());
  }

  private Map<String, MemoryPoolSnapshot> toMap(List<MemoryPoolSnapshot> pools) {

    Map<String, MemoryPoolSnapshot> map = new HashMap<>(pools.size());

    for (MemoryPoolSnapshot pool : pools) {

      map.put(pool.name, pool);
    }

    return map;
  }
}
