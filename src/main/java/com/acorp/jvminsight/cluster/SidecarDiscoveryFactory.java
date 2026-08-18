package com.acorp.jvminsight.cluster;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.config.RuntimeMode;

public final class SidecarDiscoveryFactory {

  private SidecarDiscoveryFactory() {}

  public static SidecarDiscovery create() {

    RuntimeMode mode = RuntimeMode.from(ConfigLoader.get("runtime.mode", "local"));

    return switch (mode) {
      case LOCAL -> new LocalSidecarDiscovery();

      case KUBERNETES -> new KubernetesSidecarDiscovery();
    };
  }
}
