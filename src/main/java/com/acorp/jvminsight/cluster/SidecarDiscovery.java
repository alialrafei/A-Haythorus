package com.acorp.jvminsight.cluster;

import java.net.URI;
import java.util.List;

public interface SidecarDiscovery {
  List<URI> discover();

  /** Discovers sidecars belonging to a requested shard when the discovery backend supports it. */
  default List<URI> discover(Integer shardId) {
    return discover();
  }
}
