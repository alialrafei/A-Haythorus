package com.acorp.jvminsight.cluster.kubernetes.dto;

import java.util.List;
import lombok.Data;

@Data
public class KubernetesPodList {

  private List<KubernetesPod> items;
}
