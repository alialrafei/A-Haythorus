package com.acorp.jvminsight.cluster.kubernetes.dto;

import lombok.Data;

@Data
public class KubernetesPod {

  private KubernetesMetadata metadata;

  private KubernetesPodStatus status;
}
