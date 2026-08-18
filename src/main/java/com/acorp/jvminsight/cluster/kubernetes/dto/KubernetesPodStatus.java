package com.acorp.jvminsight.cluster.kubernetes.dto;

import lombok.Data;

@Data
public class KubernetesPodStatus {

  private String phase;

  private String podIP;
}
