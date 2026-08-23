package com.acorp.jvminsight.cluster;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.container.dto.AggregatorSnapshot;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SidecarClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(SidecarClient.class);

  private static final ObjectMapper MAPPER =
    new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false);

  private final HttpClient httpClient;

  public SidecarClient() {

    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(
                Duration.ofMillis(ConfigLoader.getLong("cluster.connect.timeout.ms", 1000)))
            .build();
  }

  public AggregatorSnapshot fetchSnapshot(URI sidecarUri) {

    URI snapshotUri = sidecarUri.resolve("/api/v1/snapshot");

    HttpRequest request =
        HttpRequest.newBuilder(snapshotUri)
            .timeout(Duration.ofMillis(ConfigLoader.getLong("cluster.request.timeout.ms", 2000)))
            .header("Accept", "application/json")
            .header(ClusterHeaders.SCOPE, ClusterHeaders.LOCAL)
            .GET()
            .build();

    try {

      LOGGER.debug("Fetching snapshot from peer {}.", snapshotUri);

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {

        throw new IllegalStateException(
            "Peer " + sidecarUri + " returned HTTP " + response.statusCode());
      }

      AggregatorSnapshot snapshot = MAPPER.readValue(response.body(), AggregatorSnapshot.class);

      LOGGER.debug("Successfully fetched snapshot from peer {}.", sidecarUri);

      return snapshot;

    } catch (InterruptedException ex) {

      Thread.currentThread().interrupt();

      throw new IllegalStateException("Peer request interrupted: " + sidecarUri, ex);

    } catch (Exception ex) {

      throw new IllegalStateException("Failed to fetch peer snapshot from " + sidecarUri, ex);
    }
  }
}
