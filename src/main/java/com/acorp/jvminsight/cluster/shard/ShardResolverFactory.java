package com.acorp.jvminsight.cluster.shard;

/** Creates the configured sharding algorithm without leaking algorithm details into discovery. */
public final class ShardResolverFactory {

  private ShardResolverFactory() {}

  public static ShardResolver create(ShardConfiguration config) {
    return switch (config.algorithm()) {
      case "sha256-modulo" -> new Sha256ModuloShardResolver(config, new ShardKeyResolver());
      default ->
          throw new IllegalStateException(
              "Unsupported cluster shard algorithm: " + config.algorithm());
    };
  }
}
