package com.acorp.jvminsight.cluster.shard;

import com.acorp.jvminsight.config.ConfigLoader;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable sharding configuration loaded from application properties / environment variables.
 *
 * <p>Defaults preserve the pre-sharding behavior: sharding disabled and one shard.
 */
public record ShardConfiguration(
    boolean enabled,
    int shardCount,
    String algorithm,
    List<String> keyFields,
    String overrideLabel) {

  public static ShardConfiguration load() {
    boolean enabled =
        Boolean.parseBoolean(ConfigLoader.get("cluster.sharding.enabled", "false"));

    int shardCount = ConfigLoader.getInt("cluster.shard.count", 1);
    if (shardCount <= 0) {
      throw new IllegalStateException("Configuration 'cluster.shard.count' must be > 0.");
    }

    String algorithm =
        ConfigLoader.get("cluster.shard.algorithm", "sha256-modulo").trim().toLowerCase();

    List<String> keyFields =
        Arrays.stream(ConfigLoader.get("cluster.shard.key-fields", "namespace,pod").split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();

    if (keyFields.isEmpty()) {
      throw new IllegalStateException(
          "Configuration 'cluster.shard.key-fields' must contain at least one field.");
    }

    String overrideLabel =
        ConfigLoader.get("cluster.shard.override-label", "a-haythorus.io/shard").trim();

    return new ShardConfiguration(enabled, shardCount, algorithm, keyFields, overrideLabel);
  }
}
