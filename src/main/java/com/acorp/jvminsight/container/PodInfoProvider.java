package com.acorp.jvminsight.container;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.container.dto.PodInfo;

public final class PodInfoProvider {

  private static final PodInfo POD_INFO = build();

  private PodInfoProvider() {}

  public static PodInfo getPodInfo() {
    return POD_INFO;
  }

  private static PodInfo build() {

    PodInfo pod = new PodInfo();

    pod.setName(getEnvOrDefault("POD_NAME", ConfigLoader.get("pod.name")));

    pod.setNamespace(getEnvOrDefault("POD_NAMESPACE", ConfigLoader.get("pod.namespace")));

    pod.setNode(getEnvOrDefault("NODE_NAME", ConfigLoader.get("node.name")));

    pod.setApp(getEnvOrDefault("APP_NAME", ConfigLoader.get("app.name")));

    return pod;
  }

  private static String getEnvOrDefault(String env, String defaultValue) {

    String value = System.getenv(env);

    return value == null || value.isBlank() ? defaultValue : value;
  }
}
