package com.acorp.jvminsight.config;

public enum RuntimeMode {
  LOCAL,
  KUBERNETES;

  public static RuntimeMode from(String value) {

    if (value == null || value.isBlank()) {

      return LOCAL;
    }

    try {

      return RuntimeMode.valueOf(value.trim().toUpperCase());

    } catch (IllegalArgumentException ex) {

      throw new IllegalStateException("Unsupported runtime.mode: " + value);
    }
  }
}
