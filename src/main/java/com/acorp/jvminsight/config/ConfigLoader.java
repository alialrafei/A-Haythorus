package com.acorp.jvminsight.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);

  private static final Properties PROPERTIES = loadProperties();

  private static final Map<String, String> ENV_MAPPING =
      Map.ofEntries(
          Map.entry("runtime.mode", "AH_RUNTIME_MODE"),
          Map.entry("server.host", "AH_SERVER_HOST"),
          Map.entry("server.port", "AH_SERVER_PORT"),
          Map.entry("sidecar.peers", "AH_SIDECAR_PEERS"),
          Map.entry("sidecar.discovery.label", "AH_DISCOVERY_LABEL"),
          Map.entry("collector.interval.ms", "AH_COLLECTOR_INTERVAL_MS"),
          Map.entry("cluster.connect.timeout.ms", "AH_CLUSTER_CONNECT_TIMEOUT_MS"),
          Map.entry("cluster.request.timeout.ms", "AH_CLUSTER_REQUEST_TIMEOUT_MS"),
          Map.entry("pod.name", "POD_NAME"),
          Map.entry("pod.namespace", "POD_NAMESPACE"),
          Map.entry("pod.node", "NODE_NAME"),
          Map.entry("pod.ip", "POD_IP"),
          Map.entry("pod.app", "APP_NAME"));

  private ConfigLoader() {}

  public static String get(String key) {

    String envName = ENV_MAPPING.get(key);

    if (envName != null) {

      String envValue = System.getenv(envName);

      if (envValue != null && !envValue.isBlank()) {

        return envValue.trim();
      }
    }

    return PROPERTIES.getProperty(key);
  }

  public static String get(String key, String defaultValue) {

    String value = get(key);

    return value == null || value.isBlank() ? defaultValue : value;
  }

  public static int getInt(String key, int defaultValue) {

    String value = get(key);

    if (value == null || value.isBlank()) {
      return defaultValue;
    }

    try {

      return Integer.parseInt(value.trim());

    } catch (NumberFormatException ex) {

      throw new IllegalStateException(
          "Configuration '" + key + "' must be an integer but was '" + value + "'", ex);
    }
  }

  public static long getLong(String key, long defaultValue) {

    String value = get(key);

    if (value == null || value.isBlank()) {
      return defaultValue;
    }

    try {

      return Long.parseLong(value.trim());

    } catch (NumberFormatException ex) {

      throw new IllegalStateException(
          "Configuration '" + key + "' must be a long but was '" + value + "'", ex);
    }
  }

  private static Properties loadProperties() {

    Properties props = new Properties();

    try (InputStream input =
        ConfigLoader.class.getClassLoader().getResourceAsStream("application.properties")) {

      if (input == null) {

        LOGGER.warn("application.properties was not found.");

        return props;
      }

      props.load(input);

      return props;

    } catch (IOException ex) {

      throw new IllegalStateException("Unable to load application.properties", ex);
    }
  }
}
