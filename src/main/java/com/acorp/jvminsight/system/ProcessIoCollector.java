package com.acorp.jvminsight.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Reads cumulative per-process Linux I/O counters from /proc/<pid>/io. */
public final class ProcessIoCollector {

  private ProcessIoCollector() {}

  public static ProcessIoSnapshot collect(long pid) throws IOException {
    Path ioFile = Path.of("/proc", Long.toString(pid), "io");
    Map<String, Long> values = new HashMap<>();

    for (String line : Files.readAllLines(ioFile)) {
      int separator = line.indexOf(':');
      if (separator <= 0) {
        continue;
      }

      String key = line.substring(0, separator).trim();
      String rawValue = line.substring(separator + 1).trim();
      try {
        values.put(key, Long.parseLong(rawValue));
      } catch (NumberFormatException ignored) {
        // Ignore malformed or unsupported counters and keep the collector resilient.
      }
    }

    return new ProcessIoSnapshot(
        values.getOrDefault("rchar", 0L),
        values.getOrDefault("wchar", 0L),
        values.getOrDefault("syscr", 0L),
        values.getOrDefault("syscw", 0L),
        values.getOrDefault("read_bytes", 0L),
        values.getOrDefault("write_bytes", 0L),
        values.getOrDefault("cancelled_write_bytes", 0L));
  }
}
