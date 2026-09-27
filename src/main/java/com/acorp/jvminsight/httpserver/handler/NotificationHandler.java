package com.acorp.jvminsight.httpserver.handler;

import com.acorp.jvminsight.httpserver.util.JsonResponse;
import com.acorp.jvminsight.notification.NotificationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.List;

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
      String id = path.substring(prefix.length(), path.length() - "/evidence".length());
      if (id.isBlank()) {
        JsonResponse.badRequest(exchange, "Missing notification id.");
        return;
      }
      JsonResponse.ok(exchange, notificationService.getEvidence(id));
      return;
    }

    JsonResponse.notFound(exchange);
  }

  private String normalize(String path) {
    if (path == null || path.isBlank()) return "/";
    if (path.length() > 1 && path.endsWith("/")) return path.substring(0, path.length() - 1);
    return path;
  }
}
