package com.acorp.jvminsight.cluster.shard;

import com.acorp.jvminsight.cluster.kubernetes.dto.KubernetesPod;

/** Resolves a Kubernetes pod to a stable A-Haythorus shard id. */
public interface ShardResolver {

  int resolve(KubernetesPod pod);

  int shardCount();
}
