package com.acorp.jvminsight;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.discovery.JvmProcessLocator;
import com.acorp.jvminsight.httpserver.HttpServerUtil;
import com.acorp.jvminsight.httpserver.SidecarPushScheduler;
import com.acorp.jvminsight.snapshotcollection.service.JvmCollector;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {

  public static void main(String[] args) throws Exception {
    redirctStdOut();
    List<Long> autoDetectTargetJvmPid;

    autoDetectTargetJvmPid = JvmProcessLocator.autoDetectTargetJvmPid();
    for (long pid : autoDetectTargetJvmPid) {
      Thread t = new Thread(new JvmCollector(pid), "collector-" + pid);
      t.setDaemon(true);
      t.start();
    }
    // start http server
    HttpServerUtil.StartHttpServer();

    // start aggregator push loop
    SidecarPushScheduler.start();

    // keep my app alive
    Thread.currentThread().join();
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
