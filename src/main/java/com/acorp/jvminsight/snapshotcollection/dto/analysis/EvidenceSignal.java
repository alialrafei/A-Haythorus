package com.acorp.jvminsight.snapshotcollection.dto.analysis;

/**
 * One normalized piece of analyzer evidence.
 *
 * @param name stable machine-readable signal name
 * @param value normalized value in [0,1]; ignored when unavailable
 * @param available whether the signal was actually observable
 * @param description human-readable interpretation
 */
public record EvidenceSignal(
    String name, double value, boolean available, String description) {

  public static EvidenceSignal available(String name, double value, String description) {
    return new EvidenceSignal(name, clamp01(value), true, description);
  }

  public static EvidenceSignal unavailable(String name, String description) {
    return new EvidenceSignal(name, 0.0, false, description);
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
