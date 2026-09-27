package com.acorp.jvminsight.cluster.shard;

import com.acorp.jvminsight.cluster.kubernetes.dto.KubernetesPod;

/** Resolves a Kubernetes pod to a stable A-Haythorus shard id. */
public interface ShardResolver {

  ShardMetaData resolve(KubernetesPod pod);

  /**
   * Resolves the shard from the configured key without consulting a materialized override label.
   */
  ShardMetaData resolveComputed(KubernetesPod pod);

  int shardCount();
}
