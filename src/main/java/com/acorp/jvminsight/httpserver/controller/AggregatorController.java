package com.acorp.jvminsight.httpserver.controller;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class AggregatorController {
  private AggregatorController() {}

  private static final String DEFAULT_AGGREGATOR_URL = "http://localhost:3000/ingest/snapshot";

  public static final String AGGREGATOR_URL = resolveAggregatorUrl();

  private static String resolveAggregatorUrl() {
    String env = System.getenv("AGGREGATOR_URL");

    if (env == null || env.isBlank()) {
      System.err.println("[WARN] AGGREGATOR_URL not set, using default: " + DEFAULT_AGGREGATOR_URL);
      return DEFAULT_AGGREGATOR_URL;
    }

    return env;
  }

  public static void push(String json) {

    if (json == null) return;

    try {
      HttpURLConnection conn = (HttpURLConnection) new URL(resolveAggregatorUrl()).openConnection();

      conn.setRequestMethod("POST");
      conn.setConnectTimeout(1000);
      conn.setReadTimeout(1000);
      conn.setDoOutput(true);
      conn.setRequestProperty("Content-Type", "application/json");

      try (OutputStream os = conn.getOutputStream()) {
        os.write(json.getBytes(StandardCharsets.UTF_8));
      }

      conn.getResponseCode();
      conn.disconnect();

    } catch (Exception e) {

      System.err.println("Aggregator push failed: " + e.getMessage());
    }
  }
}
