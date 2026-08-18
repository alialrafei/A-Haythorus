package com.acorp.jvminsight.cluster;

import com.acorp.jvminsight.config.ConfigLoader;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

public class LocalSidecarDiscovery implements SidecarDiscovery {

  @Override
  public List<URI> discover() {

    String configuredPeers = ConfigLoader.get("sidecar.peers", "");

    if (configuredPeers.isBlank()) {
      return List.of();
    }

    return Arrays.stream(configuredPeers.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(URI::create)
        .distinct()
        .toList();
  }
}
