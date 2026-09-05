package com.acorp.jvminsight.cluster.shard;

import com.acorp.jvminsight.config.ConfigLoader;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    String overrideLabel,
    String missingKeyPolicy) {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShardConfiguration.class);

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

    String missingKeyPolicy =
        ConfigLoader.get("cluster.shard.missing-key-policy", "fallback").trim().toLowerCase();

    if (!List.of("fallback", "reject").contains(missingKeyPolicy)) {
      throw new IllegalStateException(
          "Configuration 'cluster.shard.missing-key-policy' must be 'fallback' or 'reject'.");
    }

    if (enabled && shardCount > 1 && keyFields.stream().noneMatch("pod"::equals)) {
      LOGGER.warn(
          "Shard key fields {} do not include per-pod identity; large replica groups may concentrate in one shard.",
          keyFields);
    }

    return new ShardConfiguration(
        enabled, shardCount, algorithm, keyFields, overrideLabel, missingKeyPolicy);
  }
}
