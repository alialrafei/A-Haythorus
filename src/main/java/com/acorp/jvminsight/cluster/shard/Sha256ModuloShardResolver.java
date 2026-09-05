package com.acorp.jvminsight.cluster.shard;

import com.acorp.jvminsight.cluster.kubernetes.dto.KubernetesPod;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime-neutral deterministic shard resolver.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>If the configured override label exists, use its explicit shard id.
 *   <li>Otherwise build the configured shard key.
 *   <li>Hash the key using SHA-256.
 *   <li>Interpret the first 8 digest bytes as a signed long and use floorMod by shard count.
 * </ol>
 *
 * <p>Using SHA-256 instead of Java {@link String#hashCode()} keeps shard assignment reproducible
 * from future agents written in other languages.
 */
public final class Sha256ModuloShardResolver implements ShardResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(Sha256ModuloShardResolver.class);

  private final ShardConfiguration config;
  private final ShardKeyResolver keyResolver;

  public Sha256ModuloShardResolver(ShardConfiguration config, ShardKeyResolver keyResolver) {
    this.config = config;
    this.keyResolver = keyResolver;
  }

  @Override
  public int resolve(KubernetesPod pod) {
    Integer override = explicitOverride(pod);
    if (override != null) {
      return override;
    }

    String key;
    try {
      key = keyResolver.resolve(pod, config.keyFields());
    } catch (IllegalStateException ex) {
      LOGGER.warn(
          "Unable to build configured shard key for pod {}; falling back to namespace,pod. reason={}",
          podName(pod),
          ex.getMessage());
      key = keyResolver.resolve(pod, java.util.List.of("namespace", "pod"));
    }

    byte[] digest = sha256(key);
    long hash64 = ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
    return Math.floorMod(hash64, config.shardCount());
  }

  @Override
  public int shardCount() {
    return config.shardCount();
  }

  private Integer explicitOverride(KubernetesPod pod) {
    if (pod == null || pod.getMetadata() == null || config.overrideLabel().isBlank()) {
      return null;
    }

    Map<String, String> labels =
        pod.getMetadata().getLabels() == null ? Map.of() : pod.getMetadata().getLabels();
    String value = labels.get(config.overrideLabel());
    if (value == null || value.isBlank()) {
      return null;
    }

    final int shard;
    try {
      shard = Integer.parseInt(value.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalStateException(
          "Shard override label '" + config.overrideLabel() + "' must be an integer but was '" + value + "'.",
          ex);
    }

    if (shard < 0 || shard >= config.shardCount()) {
      throw new IllegalStateException(
          "Shard override "
              + shard
              + " for pod "
              + podName(pod)
              + " is outside [0, "
              + (config.shardCount() - 1)
              + "].");
    }

    return shard;
  }

  private byte[] sha256(String key) {
    try {
      return MessageDigest.getInstance("SHA-256")
          .digest(key.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable in this JVM.", ex);
    }
  }

  private String podName(KubernetesPod pod) {
    return pod == null || pod.getMetadata() == null ? "<unknown>" : pod.getMetadata().getName();
  }
}
