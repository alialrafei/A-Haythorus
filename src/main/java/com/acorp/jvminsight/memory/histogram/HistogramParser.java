package com.acorp.jvminsight.memory.histogram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HistogramParser {
  private HistogramParser() {}

  public static List<ClassHistogramEntry> parse(String histogramText) {
    List<ClassHistogramEntry> result = new ArrayList<>();
    if (histogramText == null || histogramText.isEmpty()) return result;
    String[] lines = histogramText.split("\n");
    for (String line : lines) {
      line = line.trim();

      if (line.isEmpty() || line.contains("num") || line.contains("---")) continue;
      int colon = line.indexOf(":");

      if (colon < 0) continue;

      String data = line.substring(colon + 1).trim();
      String[] parts = data.split("\\s+", 3);
      if (parts.length < 3) continue;
      try {
        long instances = Long.parseLong(parts[0]);
        long bytes = Long.parseLong(parts[1]);
        String className = parts[2];
        result.add(new ClassHistogramEntry(className, instances, bytes));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return result;
  }

  public static List<ClassHistogramEntry> sortByBytesDesc(List<ClassHistogramEntry> entries) {

    return entries.stream().sorted(Comparator.comparingLong(e -> -e.getBytes())).toList();
  }

  public static List<ClassHistogramEntry> sortByInstancesDesc(List<ClassHistogramEntry> entries) {

    return entries.stream().sorted(Comparator.comparingLong(e -> -e.getInstances())).toList();
  }
}
