package com.acorp.jvminsight.cluster.shard;

/** Raised when a supported shard-key field is configured but absent on a particular pod. */
public final class MissingShardKeyFieldException extends RuntimeException {

  public MissingShardKeyFieldException(String message) {
    super(message);
  }
}
