package com.acorp.jvminsight.cluster.kubernetes;

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
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Materializes the configured shard assignment as a Kubernetes pod label. */
public final class KubernetesShardLabelController implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesShardLabelController.class);
  private static final Path SERVICE_ACCOUNT_DIR = Path.of("/var/run/secrets/kubernetes.io/serviceaccount");
  private static final Path TOKEN_PATH = SERVICE_ACCOUNT_DIR.resolve("token");
  private static final Path CA_PATH = SERVICE_ACCOUNT_DIR.resolve("ca.crt");
  private static final Path NAMESPACE_PATH = SERVICE_ACCOUNT_DIR.resolve("namespace");
  private static final long POLL_INTERVAL_MS = 10_000;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final ShardConfiguration configuration;
  private final ShardResolver resolver;
  private final HttpClient httpClient;

  public KubernetesShardLabelController() {
    this.configuration = ShardConfiguration.load();
    this.resolver = ShardResolverFactory.create(configuration);
    this.httpClient =
        HttpClient.newBuilder()
            .sslContext(KubernetesSslContextFactory.create(CA_PATH))
            .connectTimeout(Duration.ofMillis(ConfigLoader.getLong("cluster.connect.timeout.ms", 1000)))
            .build();
  }

  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        reconcile();
      } catch (Exception ex) {
        LOGGER.warn("Failed reconciling Kubernetes shard labels.", ex);
      }

      try {
        Thread.sleep(POLL_INTERVAL_MS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void reconcile() throws Exception {
    String namespace = readRequiredFile(NAMESPACE_PATH);
    String token = readRequiredFile(TOKEN_PATH);
    String apiHost = requiredEnvironment("KUBERNETES_SERVICE_HOST");
    String apiPort = System.getenv().getOrDefault("KUBERNETES_SERVICE_PORT_HTTPS", "443");
    String discoveryLabel = ConfigLoader.get("sidecar.discovery.label", "a-haythorus.io/enabled=true");

    URI uri = URI.create(
        "https://" + apiHost + ":" + apiPort + "/api/v1/namespaces/" + namespace
            + "/pods?labelSelector=" + URLEncoder.encode(discoveryLabel, StandardCharsets.UTF_8));

    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMillis(ConfigLoader.getLong("cluster.request.timeout.ms", 2000)))
        .header("Authorization", "Bearer " + token)
        .header("Accept", "application/json")
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("Kubernetes API returned HTTP " + response.statusCode());
    }

    KubernetesPodList podList = MAPPER.readValue(response.body(), KubernetesPodList.class);
    if (podList == null || podList.getItems() == null) {
      return;
    }

    for (KubernetesPod pod : podList.getItems()) {
      if (!isRunning(pod) || pod.getMetadata() == null || pod.getMetadata().getName() == null) {
        continue;
      }

      int shard = resolver.resolveComputed(pod);
      String current = pod.getMetadata().getLabels() == null
          ? null
          : pod.getMetadata().getLabels().get(configuration.overrideLabel());

      if (!Integer.toString(shard).equals(current)) {
        patchShardLabel(apiHost, apiPort, namespace, pod.getMetadata().getName(), token, shard);
      }
    }
  }

  private void patchShardLabel(String apiHost, String apiPort, String namespace, String podName,
      String token, int shard) throws Exception {
    URI uri = URI.create("https://" + apiHost + ":" + apiPort + "/api/v1/namespaces/"
        + namespace + "/pods/" + URLEncoder.encode(podName, StandardCharsets.UTF_8));

    Map<String, Object> labels = new HashMap<>();
    labels.put(configuration.overrideLabel(), Integer.toString(shard));
    Map<String, Object> metadata = Map.of("labels", labels);
    String body = MAPPER.writeValueAsString(Map.of("metadata", metadata));

    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMillis(ConfigLoader.getLong("cluster.request.timeout.ms", 2000)))
        .header("Authorization", "Bearer " + token)
        .header("Accept", "application/json")
        .header("Content-Type", "application/merge-patch+json")
        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Failed to label pod " + podName + ": Kubernetes API returned HTTP " + response.statusCode());
    }

    LOGGER.info("Materialized shard label for pod {}: {}={}", podName, configuration.overrideLabel(), shard);
  }

  private boolean isRunning(KubernetesPod pod) {
    return pod != null && pod.getStatus() != null
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
