package com.acorp.jvminsight.httpserver.util;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class PathUtils {

  private PathUtils() {}

  public static List<String> getPathSegments(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      return List.of();
    }

    return Arrays.stream(rawPath.split("/"))
        .filter(segment -> !segment.isBlank())
        .collect(Collectors.toUnmodifiableList());
  }
}
