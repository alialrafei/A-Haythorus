package com.acorp.jvminsight.httpserver.handler;

import com.acorp.jvminsight.cluster.ClusterHeaders;
import com.acorp.jvminsight.cluster.ClusterHistoryService;
import com.acorp.jvminsight.cluster.ClusterSnapshotService;
import com.acorp.jvminsight.container.dto.AggregatorSnapshot;
import com.acorp.jvminsight.httpserver.constant.RouteConstants;
import com.acorp.jvminsight.httpserver.service.SnapshotService;
import com.acorp.jvminsight.httpserver.util.JsonResponse;
import com.acorp.jvminsight.snapshotcollection.dto.JvmHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SnapshotHandler implements HttpHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotHandler.class);
  private static final ClusterSnapshotService CLUSTER_SNAPSHOT_SERVICE =
      new ClusterSnapshotService();
  private static final ClusterHistoryService CLUSTER_HISTORY_SERVICE = new ClusterHistoryService();

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      JsonResponse.methodNotAllowed(exchange);
      return;
    }

    String path = normalizePath(exchange.getRequestURI().getPath());
    LOGGER.debug("Handling snapshot API request '{}'.", path);

    try {
      if (RouteConstants.SNAPSHOT.equals(path)) {
        if (isLocalOnlyRequest(exchange)) {
          JsonResponse.ok(exchange, SnapshotService.getSnapshot());
        } else {
          JsonResponse.ok(exchange, CLUSTER_SNAPSHOT_SERVICE.getSnapshots());
        }
        return;
      }

      if (RouteConstants.HISTORY.equals(path)) {
        if (isLocalOnlyRequest(exchange)) {
          JsonResponse.ok(exchange, SnapshotService.getLocalJvmHistories());
        } else {
          JsonResponse.ok(exchange, CLUSTER_HISTORY_SERVICE.getHistories());
        }
        return;
      }

      AggregatorSnapshot snapshot = SnapshotService.getSnapshot();

      if (RouteConstants.JVMS.equals(path)) {
        List<JvmSnapshot> jvms = snapshot.getJvmSnapshots();
        JsonResponse.ok(exchange, jvms == null ? List.of() : jvms);
        return;
      }

      if (path.startsWith(RouteConstants.JVMS + "/")) {
        handleJvmRoute(exchange, snapshot, path);
        return;
      }

      JsonResponse.notFound(exchange);
    } catch (Exception ex) {
      LOGGER.error("Failed to process request '{}'.", path, ex);
      JsonResponse.internalServerError(exchange, "Failed to process snapshot request.");
    }
  }

  private void handleJvmRoute(
      HttpExchange exchange, AggregatorSnapshot aggregatorSnapshot, String path)
      throws IOException {
    String remaining = path.substring((RouteConstants.JVMS + "/").length());
    String[] segments = remaining.split("/");

    if (segments.length == 0 || segments[0].isBlank()) {
      JsonResponse.badRequest(exchange, "Missing JVM PID");
      return;
    }
    if (segments.length > 2) {
      JsonResponse.notFound(exchange);
      return;
    }

    long pid;
    try {
      pid = Long.parseLong(segments[0]);
    } catch (NumberFormatException ex) {
      JsonResponse.badRequest(exchange, "Invalid JVM PID: " + segments[0]);
      return;
    }

    Optional<JvmSnapshot> result = findJvm(aggregatorSnapshot, pid);
    if (result.isEmpty()) {
      JsonResponse.notFound(exchange, "No JVM snapshot found for PID " + pid);
      return;
    }

    JvmSnapshot jvm = result.get();
    if (segments.length == 1) {
      JsonResponse.ok(exchange, jvm);
      return;
    }

    routeJvmResource(exchange, jvm, segments[1]);
  }

  private void routeJvmResource(HttpExchange exchange, JvmSnapshot snapshot, String resource)
      throws IOException {
    switch (resource) {
      case RouteConstants.MEMORY -> JsonResponse.ok(exchange, snapshot.getMemory());
      case RouteConstants.MEMORY_POOLS -> JsonResponse.ok(exchange, snapshot.getPools());
      case RouteConstants.GC -> JsonResponse.ok(exchange, snapshot.getGc());
      case RouteConstants.HISTOGRAM -> JsonResponse.ok(exchange, snapshot.getHistogram());
      case RouteConstants.THREADS -> JsonResponse.ok(exchange, snapshot.getDumpSnapshot());
      case RouteConstants.THREAD_INFO -> JsonResponse.ok(exchange, snapshot.getThreadsInfos());
      case RouteConstants.THREAD_COUNT -> handleThreadCount(exchange, snapshot);
      case RouteConstants.THREAD_CPU_TIMES ->
          JsonResponse.ok(exchange, snapshot.getThreadCpuTimes());
      case RouteConstants.ANALYSIS -> JsonResponse.ok(exchange, snapshot.getDelta());
      case RouteConstants.DEADLOCKS -> handleDeadlocks(exchange, snapshot);
      case RouteConstants.TIMESTAMP -> handleTimestamp(exchange, snapshot);
      case RouteConstants.JVM_HISTORY ->
          handleHistory(
              exchange, SnapshotService.getJvmHistory(snapshot.getPid()), snapshot.getPid());
      default -> JsonResponse.notFound(exchange, "Unknown JVM resource: " + resource);
    }
  }

  private void handleThreadCount(HttpExchange exchange, JvmSnapshot snapshot) throws IOException {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("pid", snapshot.getPid());
    response.put("threadCount", snapshot.getThreadCount());
    response.put("timestamp", snapshot.getTimestamp());
    JsonResponse.ok(exchange, response);
  }

  private void handleDeadlocks(HttpExchange exchange, JvmSnapshot snapshot) throws IOException {
    long[] deadlockIds = snapshot.getDeadlocks();
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("pid", snapshot.getPid());
    response.put("count", deadlockIds == null ? 0 : deadlockIds.length);
    response.put("threadIds", deadlockIds == null ? new long[0] : deadlockIds);
    response.put("threads", snapshot.getThreadsInfos());
    response.put("timestamp", snapshot.getTimestamp());
    JsonResponse.ok(exchange, response);
  }

  private void handleTimestamp(HttpExchange exchange, JvmSnapshot snapshot) throws IOException {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("pid", snapshot.getPid());
    response.put("timestamp", snapshot.getTimestamp());
    JsonResponse.ok(exchange, response);
  }

  private void handleHistory(HttpExchange exchange, List<JvmHistorySample> history, long pid)
      throws IOException {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("pid", pid);
    response.put("timestamp", Instant.now());
    response.put("history", history);
    JsonResponse.ok(exchange, response);
  }

  private Optional<JvmSnapshot> findJvm(AggregatorSnapshot snapshot, long pid) {
    if (snapshot.getJvmSnapshots() == null) {
      return Optional.empty();
    }
    return snapshot.getJvmSnapshots().stream().filter(jvm -> jvm.getPid() == pid).findFirst();
  }

  private String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return "/";
    }
    if (path.length() > 1 && path.endsWith("/")) {
      return path.substring(0, path.length() - 1);
    }
    return path;
  }

  private boolean isLocalOnlyRequest(HttpExchange exchange) {
    String scope = exchange.getRequestHeaders().getFirst(ClusterHeaders.SCOPE);
    return ClusterHeaders.LOCAL.equalsIgnoreCase(scope);
  }
}
