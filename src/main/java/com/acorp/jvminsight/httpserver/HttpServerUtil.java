package com.acorp.jvminsight.httpserver;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.httpserver.constant.RouteConstants;
import com.acorp.jvminsight.httpserver.handler.RootHandler;
import com.acorp.jvminsight.httpserver.handler.SnapshotHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpServerUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerUtil.class);

  private HttpServerUtil() {}

  public static void startHttpServer() {
    int port = resolvePort();
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

      SnapshotHandler snapshotHandler = new SnapshotHandler();

      server.createContext(RouteConstants.ROOT, new RootHandler());

      server.createContext(RouteConstants.SNAPSHOT, snapshotHandler);

      server.createContext(RouteConstants.JVMS, snapshotHandler);

      int threadCount = Math.max(4, Runtime.getRuntime().availableProcessors());

      server.setExecutor(Executors.newFixedThreadPool(threadCount));

      server.start();

      LOGGER.info(
          "A-Haythorus HTTP server started on port {} with {} worker threads.", port, threadCount);

    } catch (IOException ex) {

      LOGGER.error("Failed to start HTTP server on port {}.", port, ex);

      throw new IllegalStateException("Unable to start HTTP server on port " + port, ex);
    }
  }

  private static int resolvePort() {
    String value = ConfigLoader.get("server.port");
    if (value == null || value.isBlank()) {
      LOGGER.warn("server.port is not configured. Falling back to 8899.");
      return 8899;
    }
    try {
      int port = Integer.parseInt(value);

      if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("Invalid server.port: " + port);
      }
      return port;

    } catch (NumberFormatException ex) {
      throw new IllegalStateException("server.port must be a valid integer: " + value, ex);
    }
  }
}
