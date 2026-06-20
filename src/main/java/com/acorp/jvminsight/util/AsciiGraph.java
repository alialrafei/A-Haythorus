package com.acorp.jvminsight.util;

import java.text.DecimalFormat;

public final class AsciiGraph {

  private AsciiGraph() {}

  private static final DecimalFormat df = new DecimalFormat("0.00");

  public static String bar(long used, long max, int width) {
    if (max <= 0) return "";

    int filled = (int) ((double) used / max * width);
    return "[" + "#".repeat(Math.max(0, filled)) + " ".repeat(Math.max(0, width - filled)) + "]";
  }

  public static double mb(long bytes) {
    return Double.parseDouble(df.format(bytes / 1024 / 1024));
  }
}
