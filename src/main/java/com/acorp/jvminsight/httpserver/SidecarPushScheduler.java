package com.acorp.jvminsight.httpserver;

import com.acorp.jvminsight.httpserver.controller.AggregatorController;
import com.acorp.jvminsight.httpserver.service.SidecarPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SidecarPushScheduler {
  private static final Logger LOGGER = LoggerFactory.getLogger(SidecarPushScheduler.class);
  private static final long PUSH_INTERVAL_MS = 5500;

  private SidecarPushScheduler() {}

  public static void start() {
    LOGGER.info("Starting sidecar push scheduler with interval {} ms.", PUSH_INTERVAL_MS);
    Thread t =
        new Thread(
            () -> {
              LOGGER.info("Sidecar push thread '{}' started.", Thread.currentThread().getName());
              while (true) {
                try {
                  LOGGER.debug("Starting snapshot collection and push cycle.");
                  Thread.sleep(PUSH_INTERVAL_MS);
                  String json = SidecarPushService.buildSnapshotJson();
                  if (json == null) {
                    LOGGER.warn("Skipping aggregator push because payload generation failed.");
                    continue;
                  }
                  LOGGER.info("Generated snapshot payload successfully ({} bytes).", json.length());
                  LOGGER.info(json);
                  AggregatorController.push(json);
                  LOGGER.info("Snapshot pushed successfully to aggregator.");

                } catch (InterruptedException ex) {
                  LOGGER.warn("Sidecar push thread interrupted. Stopping scheduler.", ex);

                  Thread.currentThread().interrupt();
                  break;
                } catch (Exception ex) {

                  LOGGER.error("Unexpected exception during sidecar push cycle.", ex);
                }
              }
              LOGGER.info("Sidecar push thread '{}' terminated.", Thread.currentThread().getName());
            },
            "sidecar-push-thread");

    t.setDaemon(true);
    LOGGER.info("Created daemon thread '{}'.", t.getName());
    t.start();
    LOGGER.info("Sidecar push scheduler started successfully.");
  }
}
