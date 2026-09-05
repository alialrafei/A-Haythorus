package com.acorp.jvminsight.cluster;

import com.acorp.jvminsight.cluster.kubernetes.KubernetesSslContextFactory;
import com.acorp.jvminsight.cluster.kubernetes.dto.KubernetesPod;
import com.acorp.jvminsight.cluster.kubernetes.dto.KubernetesPodList;
import com.acorp.jvminsight.cluster.shard.ShardConfiguration;
import com.acorp.jvminsight.cluster.shard.ShardResolver;
import com.acorp.jvminsight.cluster.shard.ShardResolverFactory;
import com.acorp.jvminsight.config.ConfigLoader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KubernetesSidecarDiscovery implements SidecarDiscovery {

  private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesSidecarDiscovery.class);

  private static final Path SERVICE_ACCOUNT_DIR =
      Path.of("/var/run/secrets/kubernetes.io/serviceaccount");

  private static final Path TOKEN_PATH = SERVICE_ACCOUNT_DIR.resolve("token");
  private static final Path CA_PATH = SERVICE_ACCOUNT_DIR.resolve("ca.crt");
  private static final Path NAMESPACE_PATH = SERVICE_ACCOUNT_DIR.resolve("namespace");

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final HttpClient httpClient;
  private final ShardConfiguration shardConfiguration;
  private final ShardResolver shardResolver;

  public KubernetesSidecarDiscovery() {
    this.shardConfiguration = ShardConfiguration.load();
    this.shardResolver = ShardResolverFactory.create(shardConfiguration);

    this.httpClient =
        HttpClient.newBuilder()
            .sslContext(KubernetesSslContextFactory.create(CA_PATH))
            .connectTimeout(
                Duration.ofMillis(ConfigLoader.getLong("cluster.connect.timeout.ms", 1000)))
            .build();
  }

  @Override
  public List<URI> discover() {
    try {
      String namespace = readRequiredFile(NAMESPACE_PATH);
      String token = readRequiredFile(TOKEN_PATH);
      String apiHost = requiredEnvironment("KUBERNETES_SERVICE_HOST");
      String apiPort = System.getenv().getOrDefault("KUBERNETES_SERVICE_PORT_HTTPS", "443");

      String labelSelector =
          ConfigLoader.get("sidecar.discovery.label", "a-haythorus.io/enabled=true");
      String encodedSelector = URLEncoder.encode(labelSelector, StandardCharsets.UTF_8);

      URI uri =
          URI.create(
              "https://"
                  + apiHost
                  + ":"
                  + apiPort
                  + "/api/v1/namespaces/"
                  + namespace
                  + "/pods?labelSelector="
                  + encodedSelector);

      HttpRequest request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofMillis(ConfigLoader.getLong("cluster.request.timeout.ms", 2000)))
              .header("Authorization", "Bearer " + token)
              .header("Accept", "application/json")
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new IllegalStateException("Kubernetes API returned HTTP " + response.statusCode());
      }

      KubernetesPodList podList = MAPPER.readValue(response.body(), KubernetesPodList.class);
      return buildSidecarUris(podList);

    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Kubernetes discovery interrupted", ex);
    } catch (Exception ex) {
      throw new IllegalStateException("Kubernetes sidecar discovery failed", ex);
    }
  }

  private List<URI> buildSidecarUris(KubernetesPodList podList) {
    if (podList == null || podList.getItems() == null) {
      return List.of();
    }

    String selfIp = ConfigLoader.get("pod.ip", "");
    String selfName = ConfigLoader.get("pod.name", "");
    int sidecarPort = ConfigLoader.getInt("server.port", 8899);

    Integer localShard = resolveLocalShard(podList, selfIp, selfName);

    List<URI> peers =
        podList.getItems().stream()
            .filter(this::isRunning)
            .filter(pod -> pod.getStatus().getPodIP() != null)
            .filter(pod -> !pod.getStatus().getPodIP().isBlank())
            .filter(pod -> !pod.getStatus().getPodIP().equals(selfIp))
            .filter(pod -> belongsToLocalShard(pod, localShard))
            .map(pod -> URI.create("http://" + pod.getStatus().getPodIP() + ":" + sidecarPort))
            .distinct()
            .toList();

    if (shardConfiguration.enabled()) {
      LOGGER.debug(
          "Kubernetes discovery selected {} peer(s) for shard {}/{}",
          peers.size(),
          localShard,
          shardConfiguration.shardCount());
    }

    return peers;
  }

  private Integer resolveLocalShard(KubernetesPodList podList, String selfIp, String selfName) {
    if (!shardConfiguration.enabled() || shardConfiguration.shardCount() == 1) {
      return null;
    }

    KubernetesPod self =
        podList.getItems().stream()
            .filter(this::isRunning)
            .filter(
                pod ->
                    (pod.getStatus() != null && selfIp.equals(pod.getStatus().getPodIP()))
                        || (pod.getMetadata() != null
                            && !selfName.isBlank()
                            && selfName.equals(pod.getMetadata().getName())))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to locate the local pod in Kubernetes discovery results for sharding."));

    return shardResolver.resolve(self);
  }

  private boolean belongsToLocalShard(KubernetesPod pod, Integer localShard) {
    if (localShard == null) {
      return true;
    }

    return shardResolver.resolve(pod) == localShard;
  }

  private boolean isRunning(KubernetesPod pod) {
    return pod != null
        && pod.getStatus() != null
        && "Running".equalsIgnoreCase(pod.getStatus().getPhase());
  }

  private String readRequiredFile(Path path) throws Exception {
    String value = Files.readString(path).trim();
    if (value.isBlank()) {
      throw new IllegalStateException("Required Kubernetes file is empty: " + path);
    }
    return value;
  }

  private String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Required environment variable missing: " + name);
    }
    return value;
  }
}
