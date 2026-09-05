package com.acorp.jvminsight.cluster.shard;

import com.acorp.jvminsight.cluster.kubernetes.dto.KubernetesPod;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Builds a deterministic sharding key from configured Kubernetes pod metadata.
 *
 * <p>Supported fields:
 *
 * <ul>
 *   <li>{@code namespace}
 *   <li>{@code pod}
 *   <li>{@code app} (label {@code app.kubernetes.io/name}, then {@code app})
 *   <li>{@code node} (currently unavailable from discovery DTO and therefore empty)
 *   <li>{@code label:<name>}
 * </ul>
 */
public final class ShardKeyResolver {

  public String resolve(KubernetesPod pod, List<String> fields) {
    if (pod == null || pod.getMetadata() == null) {
      throw new IllegalArgumentException("Pod metadata is required for shard key resolution.");
    }

    StringJoiner joiner = new StringJoiner("/");
    for (String field : fields) {
      String value = resolveField(pod, field);
      if (value == null || value.isBlank()) {
        throw new IllegalStateException(
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

    if ("namespace".equals(normalized)) {
      return pod.getMetadata().getNamespace();
    }

    if ("pod".equals(normalized)) {
      return pod.getMetadata().getName();
    }

    if ("app".equals(normalized)) {
      String value = labels.get("app.kubernetes.io/name");
      return value != null ? value : labels.get("app");
    }

    if (normalized.startsWith("label:")) {
      return labels.get(normalized.substring("label:".length()));
    }

    if ("node".equals(normalized)) {
      throw new IllegalStateException(
          "Shard key field 'node' is not available from the current Kubernetes pod DTO yet.");
    }

    throw new IllegalStateException("Unsupported shard key field: " + field);
  }
}
