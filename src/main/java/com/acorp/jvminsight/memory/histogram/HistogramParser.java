package com.acorp.jvminsight.memory.histogram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HistogramParser {

  private static final Logger LOGGER = LoggerFactory.getLogger(HistogramParser.class);

  private static final List<String> SYSTEM_PREFIXES =
      List.of("java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.");

  private static final List<String> LIBRARY_PREFIXES =
      List.of(
          "org.springframework.",
          "org.hibernate.",
          "org.apache.",
          "org.slf4j.",
          "ch.qos.logback.",
          "com.fasterxml.jackson.",
          "io.netty.",
          "reactor.",
          "org.jboss.",
          "org.eclipse.",
          "org.postgresql.",
          "com.mysql.",
          "oracle.jdbc.",
          "com.zaxxer.",
          "org.objectweb.asm.",
          "io.micrometer.",
          "org.flywaydb.",
          "com.google.",
          "org.checkerframework.",
          "com.overzealous.remark",
          "org.jacoco.",
          "org.jooq.",
          "org.jline.",
          "org.jctools.",
          "org.jooq.",
          "org.jooq.impl.",
          "org.jooq.meta.",
          "org.jooq.util.",
          "org.jooq.tools.",
          "org.jooq.util.jaxb.",
          "org.jooq.util.jaxb.tools.",
          "org.jooq.util.jaxb.tools.jooq.",
          "org.jooq.util.jaxb.tools.jooq.impl.",
          "org.jooq.util.jaxb.tools.jooq.meta.",
          "apache.commons.",
          "org.jsoup",
          "org.jetbrains.",
          "org.lombokweb");

  private static final List<String> GENERATED_MARKERS =
      List.of(
          "$$Lambda$",
          "$Proxy",
          "$$EnhancerBy",
          "$$FastClassBy",
          "$$SpringCGLIB$$",
          "$HibernateProxy$",
          "ByteBuddy",
          "CGLIB");

  private HistogramParser() {}

  /**
   * Parses a JVM class histogram and keeps only likely application-defined classes.
   *
   * <p>Runtime, JDK, framework, library, generated and array classes are filtered during parsing so
   * no secondary filtering pass is required.
   */
  public static List<ClassHistogramEntry> parse(String histogramText) {
    List<ClassHistogramEntry> result = new ArrayList<>();
    if (histogramText == null || histogramText.isBlank()) {
      return result;
    }
    String[] lines = histogramText.split("\\R");
    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (shouldSkipLine(line)) {
        continue;
      }
      int colon = line.indexOf(':');
      if (colon < 0) {
        continue;
      }
      String data = line.substring(colon + 1).trim();
      String[] parts = data.split("\\s+", 3);
      if (parts.length < 3) {
        continue;
      }
      try {
        long instances = Long.parseLong(parts[0]);
        long bytes = Long.parseLong(parts[1]);
        String className = normalizeClassName(parts[2]);
        if (!isApplicationClass(className)) {
          continue;
        }
        result.add(new ClassHistogramEntry(className, instances, bytes));
      } catch (NumberFormatException ex) {
        LOGGER.debug("Skipping malformed histogram line: {}", line);
      }
    }

    return result;
  }

  public static List<ClassHistogramEntry> sortByBytesDesc(List<ClassHistogramEntry> entries) {

    if (entries == null || entries.isEmpty()) {

      return List.of();
    }

    return entries.stream()
        .sorted(Comparator.comparingLong(ClassHistogramEntry::getBytes).reversed())
        .toList();
  }

  public static List<ClassHistogramEntry> sortByInstancesDesc(List<ClassHistogramEntry> entries) {

    if (entries == null || entries.isEmpty()) {

      return List.of();
    }

    return entries.stream()
        .sorted(Comparator.comparingLong(ClassHistogramEntry::getInstances).reversed())
        .toList();
  }

  private static boolean shouldSkipLine(String line) {

    if (line == null || line.isBlank()) {

      return true;
    }

    return line.contains("num") || line.startsWith("---") || line.startsWith("Total");
  }

  private static boolean isApplicationClass(String className) {

    if (className == null || className.isBlank()) {

      return false;
    }

    if (isArray(className)) {
      return false;
    }

    if (matchesPrefix(className, SYSTEM_PREFIXES)) {

      return false;
    }

    if (matchesPrefix(className, LIBRARY_PREFIXES)) {

      return false;
    }

    if (isGeneratedClass(className)) {
      return false;
    }

    return true;
  }

  private static boolean matchesPrefix(String className, List<String> prefixes) {

    return prefixes.stream().anyMatch(className::startsWith);
  }

  private static boolean isGeneratedClass(String className) {

    return GENERATED_MARKERS.stream().anyMatch(className::contains);
  }

  private static boolean isArray(String className) {

    /*
     * JVM histogram examples:
     *
     * [B
     * [C
     * [I
     * [Ljava.lang.Object;
     */
    return className.startsWith("[");
  }

  private static String normalizeClassName(String rawClassName) {

    if (rawClassName == null) {
      return "";
    }

    String className = rawClassName.trim();

    /*
     * Some JDK histogram outputs append metadata after
     * the actual class name.
     *
     * Example:
     *
     * com.foo.Order (some metadata)
     *
     * For now we preserve the first token as the class
     * identity because the histogram parser is interested
     * in the class itself.
     */
    int whitespace = className.indexOf(' ');

    if (whitespace > 0) {
      className = className.substring(0, whitespace);
    }

    return className;
  }
}
