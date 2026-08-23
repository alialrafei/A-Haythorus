package com.acorp.jvminsight.httpserver.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StaticUiHandler implements HttpHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(StaticUiHandler.class);
  private static final String UI_PREFIX = "/ui";

  private final Path uiRoot;

  public StaticUiHandler(String uiDirectory) {
    this.uiRoot = Path.of(uiDirectory).toAbsolutePath().normalize();
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();

    if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
      exchange.getResponseHeaders().set("Allow", "GET, HEAD");
      exchange.sendResponseHeaders(405, -1);
      exchange.close();
      return;
    }

    String requestPath = exchange.getRequestURI().getPath();

    if (UI_PREFIX.equals(requestPath)) {
      redirectToUiRoot(exchange);
      return;
    }

    Path target = resolveTarget(requestPath);

    if (target == null) {
      exchange.sendResponseHeaders(403, -1);
      exchange.close();
      return;
    }

    if (!Files.exists(target) || !Files.isRegularFile(target)) {
      target = uiRoot.resolve("index.html").normalize();
    }

    if (!Files.exists(target) || !Files.isRegularFile(target)) {
      LOGGER.warn("UI asset not found. uiRoot={} requestPath={}", uiRoot, requestPath);
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
      return;
    }

    byte[] body = Files.readAllBytes(target);

    Headers headers = exchange.getResponseHeaders();
    headers.set("Content-Type", contentType(target));

    if (target.getFileName().toString().equals("index.html")) {
      headers.set("Cache-Control", "no-cache");
    } else {
      headers.set("Cache-Control", "public, max-age=31536000, immutable");
    }

    if ("HEAD".equalsIgnoreCase(method)) {
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
      return;
    }

    exchange.sendResponseHeaders(200, body.length);

    try (OutputStream output = exchange.getResponseBody()) {
      output.write(body);
    }
  }

  private Path resolveTarget(String requestPath) {
    String relative = requestPath.substring(UI_PREFIX.length());

    if (relative.isBlank() || "/".equals(relative)) {
      relative = "/index.html";
    }

    Path resolved = uiRoot.resolve(relative.substring(1)).normalize();

    if (!resolved.startsWith(uiRoot)) {
      return null;
    }

    return resolved;
  }

  private static void redirectToUiRoot(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().set("Location", "/ui/");
    exchange.sendResponseHeaders(302, -1);
    exchange.close();
  }

  private static String contentType(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

    if (name.endsWith(".html")) {
      return "text/html; charset=utf-8";
    }
    if (name.endsWith(".js")) {
      return "text/javascript; charset=utf-8";
    }
    if (name.endsWith(".css")) {
      return "text/css; charset=utf-8";
    }
    if (name.endsWith(".json")) {
      return "application/json; charset=utf-8";
    }
    if (name.endsWith(".svg")) {
      return "image/svg+xml";
    }
    if (name.endsWith(".png")) {
      return "image/png";
    }
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (name.endsWith(".webp")) {
      return "image/webp";
    }
    if (name.endsWith(".ico")) {
      return "image/x-icon";
    }
    if (name.endsWith(".woff2")) {
      return "font/woff2";
    }

    return "application/octet-stream";
  }
}
