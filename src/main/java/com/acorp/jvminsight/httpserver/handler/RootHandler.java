package com.acorp.jvminsight.httpserver.handler;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.container.PodInfoProvider;
import com.acorp.jvminsight.container.dto.PodInfo;
import com.acorp.jvminsight.httpserver.util.JsonResponse;
import com.acorp.jvminsight.snapshotcollection.JvmDataStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Root API endpoint.
 *
 * <p>Returns metadata describing the running A-Haythorus sidecar and the UI capabilities exposed by
 * this instance.
 */
public final class RootHandler implements HttpHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(RootHandler.class);

  private static final String APPLICATION_NAME = "JVM Night Watch";

  private static final String VERSION = "1.0.0";

  @Override
  public void handle(HttpExchange exchange) throws IOException {

    LOGGER.debug("Handling root endpoint request.");

    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      JsonResponse.methodNotAllowed(exchange);
      return;
    }

    PodInfo podInfo = PodInfoProvider.getPodInfo();

    Map<String, Object> response = new LinkedHashMap<>();

    response.put("application", APPLICATION_NAME);
    response.put("version", VERSION);
    response.put("timestamp", System.currentTimeMillis());
    response.put("pod", podInfo);
    response.put("monitoredJvmCount", JvmDataStore.getDateStored().size());

    Map<String, Object> sharding = new LinkedHashMap<>();
    int shardCount = ConfigLoader.getInt("cluster.shard.count", 1);
    boolean shardingEnabled =
        Boolean.parseBoolean(ConfigLoader.get("cluster.sharding.enabled", "false"))
            && shardCount > 1;
    sharding.put("enabled", shardingEnabled);
    sharding.put("shardCount", shardingEnabled ? shardCount : 1);
    response.put("sharding", sharding);

    JsonResponse.ok(exchange, response);

    LOGGER.debug("Root endpoint served successfully.");
  }
}
