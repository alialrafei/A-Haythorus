package com.acorp.jvminsight.httpserver;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class HttpServerUtil {
  private HttpServerUtil() {}
  ;

  public static void StartHttpServer() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(8899), 0);
      // server.createContext("/api/jvms", new JvmListHandler());
      // server.createContext("/api/jvms/threads", new ThreadsHandler());
      // server.createContext("/api/jvms/heap", new HeapHandler());
      // server.createContext("/api/jvms/gc", new GcHandler());
      // server.createContext("/", new StaticUiHandler());
      server.setExecutor(Executors.newCachedThreadPool());
      server.start();

      System.out.println("HTTP server started on port 8899");
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }
}
