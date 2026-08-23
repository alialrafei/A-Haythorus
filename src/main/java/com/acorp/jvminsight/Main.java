package com.acorp.jvminsight;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.discovery.JvmProcessLocator;
import com.acorp.jvminsight.httpserver.HttpServerUtil;
import com.acorp.jvminsight.snapshotcollection.service.JvmCollector;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private static final long DISCOVERY_INTERVAL_MS = 3000;

  public static void main(String[] args) throws Exception {
    redirctStdOut();

    // start http server
    HttpServerUtil.startHttpServer();
    superviseJvmCollectors();
  }

  private static void superviseJvmCollectors() throws InterruptedException {
    Map<Long, Thread> collectors = new HashMap<>();
    while (!Thread.currentThread().isInterrupted()) {
      /*
       * Remove collector threads which have terminated.
       */
      collectors
          .entrySet()
          .removeIf(
              entry -> {
                Thread thread = entry.getValue();
                if (!thread.isAlive()) {
                  LOGGER.info("Removing terminated collector for pid={}", entry.getKey());
                  return true;
                }

                return false;
              });
      List<Long> discoveredPids;
      try {
        discoveredPids = JvmProcessLocator.autoDetectTargetJvmPid();
      } catch (Exception ex) {
        LOGGER.error("Failed scanning for JVM processes.", ex);
        Thread.sleep(DISCOVERY_INTERVAL_MS);
        continue;
      }
      LOGGER.debug("Discovered JVM PIDs: {}", discoveredPids);
      for (long pid : discoveredPids) {
        if (collectors.containsKey(pid)) {
          continue;
        }
        try {
          LOGGER.info("Starting collector for newly discovered JVM pid={}", pid);
          Thread collector = new Thread(new JvmCollector(pid), "collector-" + pid);
          /*
           * Fine to keep daemon=true because the supervisor
           * main thread stays alive.
           */
          collector.setDaemon(true);
          collector.start();
          collectors.put(pid, collector);

        } catch (Exception ex) {
          /*
           * Attach can race against JVM shutdown/startup.
           * Don't crash A-Haythorus; retry on next scan.
           */
          LOGGER.warn(
              "Failed starting collector for pid={}. " + "Will retry during next JVM scan.",
              pid,
              ex);
        }
      }
      Thread.sleep(DISCOVERY_INTERVAL_MS);
    }
  }

  private static void redirctStdOut() {
    boolean logFileFlag = Boolean.parseBoolean(ConfigLoader.get("logfile"));
    if (!logFileFlag) return;
    try {
      Path logdir = Paths.get("target/");
      Files.createDirectories(logdir);
      FileOutputStream fos = new FileOutputStream(logdir.resolve("app.log").toFile(), true);
      PrintStream ps = new PrintStream(fos, true);
      System.setOut(ps);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
