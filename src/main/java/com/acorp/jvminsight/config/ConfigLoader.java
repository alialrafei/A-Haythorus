package com.acorp.jvminsight.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
  private static final Properties PROPERTIES = loadProperties();

  private ConfigLoader() {}

  private static Properties loadProperties() {

    Properties props = new Properties();

    try (InputStream is =
        ConfigLoader.class.getClassLoader().getResourceAsStream("application.properties")) {

      if (is != null) {
        props.load(is);
      }

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return props;
  }

  public static String get(String key) {
    return PROPERTIES.getProperty(key);
  }
}
