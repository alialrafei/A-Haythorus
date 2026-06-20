package com.acorp.jvminsight.snapshotcollection.service.delta;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;

/**
 * * Strategy interface responsible for computing a particular * aspect of a JVM delta snapshot. * *
 *
 * <p>* Implementations must be stateless and thread-safe. * * *
 *
 * <p>* They should mutate only the supplied {@link JvmDeltaSnapshot}. *
 */
public interface DeltaComputationStrategy {
  /**
   * * Computes a particular delta and updates the provided delta DTO. * * @param previous previous
   * JVM snapshot * @param current current JVM snapshot * @param delta mutable aggregate delta
   * snapshot
   */
  void compute(JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta);
}
