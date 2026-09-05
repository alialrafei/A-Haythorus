package com.acorp.jvminsight.cluster;

import com.acorp.jvminsight.config.ConfigLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Shared bounded executor for sidecar-to-sidecar I/O.
 *
 * <p>Virtual threads keep blocking I/O cheap, while the semaphore caps simultaneous outbound peer
 * requests so one UI refresh cannot open an unbounded number of connections.
 */
public final class ClusterRequestExecutor {

  private static final int MAX_CONCURRENT_REQUESTS =
      Math.max(1, ConfigLoader.getInt("cluster.max.concurrent.requests", 8));

  private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
  private static final Semaphore PERMITS = new Semaphore(MAX_CONCURRENT_REQUESTS);

  private ClusterRequestExecutor() {}

  public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
    return CompletableFuture.supplyAsync(
        () -> {
          boolean acquired = false;
          try {
            PERMITS.acquire();
            acquired = true;
            return supplier.get();
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a cluster request permit.", ex);
          } finally {
            if (acquired) {
              PERMITS.release();
            }
          }
        },
        EXECUTOR);
  }

  public static int maxConcurrentRequests() {
    return MAX_CONCURRENT_REQUESTS;
  }
}
