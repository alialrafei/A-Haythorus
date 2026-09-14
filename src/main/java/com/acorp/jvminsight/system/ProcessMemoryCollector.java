package com.acorp.jvminsight.system;

import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/** Collects process-native memory indicators from Linux /proc and JVM buffer pools. */
public final class ProcessMemoryCollector {

  private ProcessMemoryCollector() {}

  public static ProcessMemorySnapshot collect(long pid, MBeanServerConnection mbeanServer)
      throws IOException {
    Map<String, Long> status = readStatus(pid);

    long allThreadStacks = 0L;
    Path taskDir = Path.of("/proc", Long.toString(pid), "task");
    try (var tasks = Files.list(taskDir)) {
      for (Path task : tasks.toList()) {
        try {
          allThreadStacks += readStatusValue(task.resolve("status"), "VmStk");
        } catch (IOException | SecurityException ignored) {
          // A thread can disappear between directory listing and status read.
        }
      }
    } catch (SecurityException ex) {
      throw ex;
    }

    long direct = bufferPoolBytes(mbeanServer, "direct");
    long mapped = bufferPoolBytes(mbeanServer, "mapped");

    return new ProcessMemorySnapshot(
        kb(status, "VmRSS"),
        kb(status, "RssAnon"),
        kb(status, "RssFile"),
        kb(status, "RssShmem"),
        kb(status, "VmData"),
        kb(status, "VmStk"),
        allThreadStacks,
        kb(status, "VmSwap"),
        direct,
        mapped);
  }

  private static Map<String, Long> readStatus(long pid) throws IOException {
    return readStatus(Path.of("/proc", Long.toString(pid), "status"));
  }

  private static Map<String, Long> readStatus(Path path) throws IOException {
    Map<String, Long> values = new HashMap<>();
    for (String line : Files.readAllLines(path)) {
      int separator = line.indexOf(':');
      if (separator <= 0) continue;
      String key = line.substring(0, separator).trim();
      String raw = line.substring(separator + 1).trim();
      String number = raw.split("\\s+")[0];
      try {
        values.put(key, Long.parseLong(number));
      } catch (NumberFormatException ignored) {
        // Ignore fields that are not numeric.
      }
    }
    return values;
  }

  private static long readStatusValue(Path path, String key) throws IOException {
    return kb(readStatus(path), key);
  }

  /** /proc status memory values are reported in kB on Linux. */
  private static long kb(Map<String, Long> values, String key) {
    return values.getOrDefault(key, 0L) * 1024L;
  }

  private static long bufferPoolBytes(MBeanServerConnection connection, String poolName) {
    try {
      ObjectName name = new ObjectName("java.nio:type=BufferPool,name=" + poolName);
      BufferPoolMXBean bean = ManagementFactory.newPlatformMXBeanProxy(
          connection, name.toString(), BufferPoolMXBean.class);
      return Math.max(0L, bean.getMemoryUsed());
    } catch (Exception ignored) {
      return 0L;
    }
  }
}
