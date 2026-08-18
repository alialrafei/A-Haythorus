package com.acorp.jvminsight.cluster.kubernetes.dto;

import java.util.Map;
import lombok.Data;

@Data
public class KubernetesMetadata {

  private String name;

  private String namespace;

  private Map<String, String> labels;
}
