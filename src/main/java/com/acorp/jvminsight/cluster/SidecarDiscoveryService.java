package com.acorp.jvminsight.cluster;

import java.net.URI;
import java.util.List;

public interface SidecarDiscoveryService {
  List<URI> discover();
}
