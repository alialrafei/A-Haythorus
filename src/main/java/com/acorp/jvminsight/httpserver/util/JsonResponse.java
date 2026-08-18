package com.acorp.jvminsight.httpserver.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonResponse {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private JsonResponse() {}

  public static void ok(HttpExchange exchange, Object body) throws IOException {

    send(exchange, 200, body);
  }

  public static void badRequest(HttpExchange exchange, String message) throws IOException {

    sendError(exchange, 400, "Bad Request", message);
  }

  public static void notFound(HttpExchange exchange, String message) throws IOException {

    sendError(exchange, 404, "Not Found", message);
  }

  public static void methodNotAllowed(HttpExchange exchange) throws IOException {

    sendError(
        exchange, 405, "Method Not Allowed", "HTTP method is not supported for this endpoint.");
  }

  public static void internalServerError(HttpExchange exchange, String message) throws IOException {

    sendError(exchange, 500, "Internal Server Error", message);
  }

  private static void sendError(HttpExchange exchange, int status, String error, String message)
      throws IOException {

    Map<String, Object> response = new LinkedHashMap<>();

    response.put("status", status);
    response.put("error", error);
    response.put("message", message);
    response.put("timestamp", System.currentTimeMillis());

    send(exchange, status, response);
  }

  private static void send(HttpExchange exchange, int status, Object body) throws IOException {

    byte[] response = MAPPER.writeValueAsBytes(body);

    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

    exchange.sendResponseHeaders(status, response.length);

    try (OutputStream output = exchange.getResponseBody()) {

      output.write(response);
    }
  }

  public static void notFound(HttpExchange exchange) throws IOException {

    notFound(exchange, "Requested resource was not found.");
  }
}
