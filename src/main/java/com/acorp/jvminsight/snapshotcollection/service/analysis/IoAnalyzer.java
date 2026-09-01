package com.acorp.jvminsight.snapshotcollection.service.analysis;

import com.acorp.jvminsight.snapshotcollection.dto.ProcessHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.analysis.AnalysisResult;
import com.acorp.jvminsight.snapshotcollection.dto.analysis.EvidenceSignal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runtime-neutral historical Linux process I/O analyzer.
 *
 * <p>Absolute device pressure cannot be inferred safely without a device/cgroup capacity or an
 * application baseline. This analyzer therefore reports a clearly named recent-window activity
 * score instead of pretending throughput is universally "high".
 *
 * <pre>
 * throughput_i = (deltaReadBytes + deltaWriteBytes) / deltaSeconds
 * mean         = mean(throughput_i)
 * peak         = max(throughput_i)
 * persistence  = peak == 0 ? 0 : mean / peak
 * intensity    = peak == 0 ? 0 : latest / peak
 * activity     = intensity * persistence
 * </pre>
 *
 * <p>It also derives syscall payload sizes and storage-to-requested-byte ratios to distinguish
 * many tiny syscalls from larger transfers and buffered/page-cache activity from attributed
 * storage I/O.
 */
public final class IoAnalyzer {

  private IoAnalyzer() {}

  public static AnalysisResult analyze(List<ProcessHistorySample> samples) {
    List<IoInterval> intervals = intervals(samples);

    if (intervals.isEmpty()) {
      return unavailable();
    }

    double meanThroughput =
        intervals.stream().mapToDouble(IoInterval::throughputBytesPerSecond).average().orElse(0.0);
    double peakThroughput =
        intervals.stream().mapToDouble(IoInterval::throughputBytesPerSecond).max().orElse(0.0);
    double latestThroughput = intervals.get(intervals.size() - 1).throughputBytesPerSecond();

    double persistence = peakThroughput <= 0.0 ? 0.0 : clamp01(meanThroughput / peakThroughput);
    double relativeIntensity =
        peakThroughput <= 0.0 ? 0.0 : clamp01(latestThroughput / peakThroughput);
    double activity = clamp01(relativeIntensity * persistence);

    long readCharacters = intervals.stream().mapToLong(IoInterval::readCharacters).sum();
    long writeCharacters = intervals.stream().mapToLong(IoInterval::writeCharacters).sum();
    long readBytes = intervals.stream().mapToLong(IoInterval::readBytes).sum();
    long writeBytes = intervals.stream().mapToLong(IoInterval::writeBytes).sum();
    long readSyscalls = intervals.stream().mapToLong(IoInterval::readSyscalls).sum();
    long writeSyscalls = intervals.stream().mapToLong(IoInterval::writeSyscalls).sum();

    double averageReadPayload =
        readSyscalls <= 0 ? 0.0 : readCharacters / (double) readSyscalls;
    double averageWritePayload =
        writeSyscalls <= 0 ? 0.0 : writeCharacters / (double) writeSyscalls;

    double storageReadRatio =
        readCharacters <= 0 ? 0.0 : clamp01(readBytes / (double) readCharacters);
    double storageWriteRatio =
        writeCharacters <= 0 ? 0.0 : clamp01(writeBytes / (double) writeCharacters);

    List<EvidenceSignal> evidence =
        List.of(
            EvidenceSignal.available(
                "io-relative-intensity",
                relativeIntensity,
                "Latest storage throughput relative to the recent-window peak."),
            EvidenceSignal.available(
                "io-persistence",
                persistence,
                "Average storage throughput relative to the recent-window peak."));

    Map<String, Double> metrics =
        Map.ofEntries(
            Map.entry("latestThroughputBytesPerSecond", latestThroughput),
            Map.entry("averageThroughputBytesPerSecond", meanThroughput),
            Map.entry("peakThroughputBytesPerSecond", peakThroughput),
            Map.entry("persistencePercent", persistence * 100.0),
            Map.entry("averageReadBytesPerSyscall", averageReadPayload),
            Map.entry("averageWriteBytesPerSyscall", averageWritePayload),
            Map.entry("storageReadRatioPercent", storageReadRatio * 100.0),
            Map.entry("storageWriteRatioPercent", storageWriteRatio * 100.0),
            Map.entry("intervalsAnalyzed", (double) intervals.size()));

    List<String> reasons =
        List.of(
            String.format(
                "Current storage throughput is %.0f%% of the recent peak with %.0f%% persistence.",
                relativeIntensity * 100.0, persistence * 100.0),
            String.format(
                "Average syscall payloads are %.0f read bytes and %.0f write bytes.",
                averageReadPayload, averageWritePayload));

    return new AnalysisResult(
        "io",
        "Sustained I/O activity",
        activity * 100.0,
        evidence,
        metrics,
        reasons);
  }

  private static List<IoInterval> intervals(List<ProcessHistorySample> samples) {
    List<IoInterval> result = new ArrayList<>();

    for (int i = 1; i < samples.size(); i++) {
      ProcessHistorySample previous = samples.get(i - 1);
      ProcessHistorySample current = samples.get(i);

      if (previous.timestamp() == null
          || current.timestamp() == null
          || !current.timestamp().isAfter(previous.timestamp())) {
        continue;
      }

      double seconds =
          Duration.between(previous.timestamp(), current.timestamp()).toNanos() / 1_000_000_000.0;
      if (seconds <= 0.0) {
        continue;
      }

      long readBytes = nonNegativeDelta(previous.readBytes(), current.readBytes());
      long writeBytes = nonNegativeDelta(previous.writeBytes(), current.writeBytes());
      long readCharacters = nonNegativeDelta(previous.readCharacters(), current.readCharacters());
      long writeCharacters = nonNegativeDelta(previous.writeCharacters(), current.writeCharacters());
      long readSyscalls = nonNegativeDelta(previous.readSyscalls(), current.readSyscalls());
      long writeSyscalls = nonNegativeDelta(previous.writeSyscalls(), current.writeSyscalls());

      result.add(
          new IoInterval(
              (readBytes + writeBytes) / seconds,
              readCharacters,
              writeCharacters,
              readSyscalls,
              writeSyscalls,
              readBytes,
              writeBytes));
    }

    return result;
  }

  private static long nonNegativeDelta(long previous, long current) {
    return Math.max(0L, current - previous);
  }

  private static AnalysisResult unavailable() {
    return new AnalysisResult(
        "io",
        "Sustained I/O activity",
        0.0,
        List.of(
            EvidenceSignal.unavailable(
                "io-relative-intensity", "Insufficient process I/O history."),
            EvidenceSignal.unavailable(
                "io-persistence", "Insufficient process I/O history.")),
        Map.of(),
        List.of("Insufficient process I/O history to evaluate recent activity."));
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private record IoInterval(
      double throughputBytesPerSecond,
      long readCharacters,
      long writeCharacters,
      long readSyscalls,
      long writeSyscalls,
      long readBytes,
      long writeBytes) {}
}
