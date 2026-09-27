package com.acorp.jvminsight.httpserver.handler;

import com.acorp.jvminsight.httpserver.util.JsonResponse;
import com.acorp.jvminsight.notification.NotificationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class NotificationHandler implements HttpHandler {

  private final NotificationService notificationService = NotificationService.getInstance();

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      JsonResponse.methodNotAllowed(exchange);
      return;
    }

    String path = normalize(exchange.getRequestURI().getPath());
    if ("/api/v1/notifications".equals(path)) {
      JsonResponse.ok(exchange, notificationService.getNotifications());
      return;
    }

    String prefix = "/api/v1/notifications/";
    if (path.startsWith(prefix) && path.endsWith("/evidence")) {
      String id = decode(path.substring(prefix.length(), path.length() - "/evidence".length()));
      String namespace = query(exchange, "namespace");
      String pod = query(exchange, "pod");
      String pidValue = query(exchange, "pid");
      if (id.isBlank() || namespace == null || pod == null || pidValue == null) {
        JsonResponse.badRequest(exchange, "Notification id, namespace, pod and pid are required.");
        return;
      }
      long pid;
      try {
        pid = Long.parseLong(pidValue);
      } catch (NumberFormatException ex) {
        JsonResponse.badRequest(exchange, "Invalid pid: " + pidValue);
        return;
      }
      JsonResponse.ok(exchange, notificationService.getEvidence(namespace, pod, pid, id));
      return;
    }

    JsonResponse.notFound(exchange);
  }

  private String query(HttpExchange exchange, String name) {
    String raw = exchange.getRequestURI().getRawQuery();
    if (raw == null) return null;
    for (String parameter : raw.split("&")) {
      String[] pair = parameter.split("=", 2);
      if (pair.length == 2 && name.equals(pair[0])) {
        return decode(pair[1]);
      }
    }
    return null;
  }

  private String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private String normalize(String path) {
    if (path == null || path.isBlank()) return "/";
    if (path.length() > 1 && path.endsWith("/")) return path.substring(0, path.length() - 1);
    return path;
  }
}
