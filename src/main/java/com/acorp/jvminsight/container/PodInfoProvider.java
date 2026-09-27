package com.acorp.jvminsight.container;

import com.acorp.jvminsight.cluster.shard.ShardMetaData;
import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.container.dto.PodInfo;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

public final class PodInfoProvider {

  private static final PodInfo POD_INFO = build();

  private PodInfoProvider() {}

  public static PodInfo getPodInfo() {
    return POD_INFO;
  }

  private static PodInfo build() {
    PodInfo pod = new PodInfo();

    pod.setName(getEnvOrDefault("POD_NAME", ConfigLoader.get("pod.name")));
    pod.setNamespace(getEnvOrDefault("POD_NAMESPACE", ConfigLoader.get("pod.namespace")));
    pod.setNode(getEnvOrDefault("NODE_NAME", ConfigLoader.get("pod.node")));
    pod.setApp(getEnvOrDefault("APP_NAME", ConfigLoader.get("app.name")));

    boolean shardingEnabled = ConfigLoader.getBoolean("cluster.sharding.enabled", false);
    int shardCount = ConfigLoader.getInt("cluster.shard.count", 1);

    if (shardingEnabled && shardCount > 1) {
      pod.setShard(resolveShard(pod, shardCount));
    }

    return pod;
  }

  /**
   * Keep the metadata reported to the UI on the same shard-key semantics used by Kubernetes
   * discovery. In particular, replica pod names must not affect assignment when the configured
   * key is namespace + application label.
   */
  private static ShardMetaData resolveShard(PodInfo pod, int shardCount) {
    List<String> fields =
        ConfigLoader.getList(
            "cluster.shard.key.fields", List.of("namespace", "pod"));

    StringBuilder canonical = new StringBuilder();
    for (String field : fields) {
      String value = resolveField(pod, field);
      if (value == null || value.isBlank()) {
        throw new IllegalStateException(
            "Shard key field '" + field + "' is missing for pod " + pod.getName());
      }
      if (canonical.length() > 0) {
        canonical.append('/');
      }
      canonical.append(value);
    }

    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
      BigInteger hash64 = new BigInteger(1, Arrays.copyOf(digest, Long.BYTES));
      int shardId = hash64.mod(BigInteger.valueOf(shardCount)).intValue();
      String shardName = pod.getNamespace() + "/" + (pod.getApp() == null ? "<unknown>" : pod.getApp());
      return new ShardMetaData(shardId, shardName);
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable in this JVM.", ex);
    }
  }

  private static String resolveField(PodInfo pod, String field) {
    String normalized = field.trim();
    return switch (normalized) {
      case "namespace" -> pod.getNamespace();
      case "pod" -> pod.getName();
      case "app" -> pod.getApp();
      case "node" -> pod.getNode();
      default -> {
        if (normalized.startsWith("label:")) {
          // PodInfo only carries the canonical application label as "app".
          // This keeps the common app.kubernetes.io/name key aligned with discovery.
          if ("app.kubernetes.io/name".equals(normalized.substring("label:".length()))) {
            yield pod.getApp();
          }
        }
        throw new IllegalStateException("Unsupported shard key field: " + field);
      }
    };
  }

  private static String getEnvOrDefault(String env, String defaultValue) {
    String value = System.getenv(env);
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
