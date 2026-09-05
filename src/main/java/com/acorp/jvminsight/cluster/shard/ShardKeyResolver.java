package com.acorp.jvminsight.cluster.shard;

import com.acorp.jvminsight.cluster.kubernetes.dto.KubernetesPod;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Builds a deterministic sharding key from configured Kubernetes pod metadata. */
public final class ShardKeyResolver {

  public String resolve(KubernetesPod pod, List<String> fields) {
    if (pod == null || pod.getMetadata() == null) {
      throw new IllegalArgumentException("Pod metadata is required for shard key resolution.");
    }

    StringJoiner joiner = new StringJoiner("/");
    for (String field : fields) {
      String value = resolveField(pod, field);
      if (value == null || value.isBlank()) {
        throw new MissingShardKeyFieldException(
            "Shard key field '" + field + "' is missing for pod " + pod.getMetadata().getName());
      }
      joiner.add(value);
    }

    return joiner.toString();
  }

  private String resolveField(KubernetesPod pod, String field) {
    String normalized = field.trim();
    Map<String, String> labels =
        pod.getMetadata().getLabels() == null ? Map.of() : pod.getMetadata().getLabels();

    return switch (normalized) {
      case "namespace" -> pod.getMetadata().getNamespace();
      case "pod" -> pod.getMetadata().getName();
      case "app" -> {
        String value = labels.get("app.kubernetes.io/name");
        yield value != null ? value : labels.get("app");
      }
      case "node" -> pod.getSpec() == null ? null : pod.getSpec().getNodeName();
      default -> {
        if (normalized.startsWith("label:")) {
          yield labels.get(normalized.substring("label:".length()));
        }
        throw new IllegalStateException("Unsupported shard key field: " + field);
      }
    };
  }
}
