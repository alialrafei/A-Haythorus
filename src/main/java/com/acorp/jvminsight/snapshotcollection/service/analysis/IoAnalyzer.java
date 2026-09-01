package com.acorp.jvminsight.snapshotcollection.service.analysis;

import com.acorp.jvminsight.config.ConfigLoader;
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
 * <p>Absolute device pressure cannot be inferred safely without device/cgroup capacity or an
 * application baseline. This analyzer therefore reports recent-window sustained I/O activity.
 *
 * <p>For a generic positive interval series {@code x_i}:
 *
 * <pre>
 * mean        = mean(x_i)
 * peak        = max(x_i)
 * persistence = peak == 0 ? 0 : mean / peak
 * intensity   = peak == 0 ? 0 : latest / peak
 * activity    = intensity * persistence
 * </pre>
 *
 * <p>The formula is applied independently to:
 *
 * <ul>
 *   <li>storage throughput: read_bytes + write_bytes per second
 *   <li>syscall rate: read syscalls + write syscalls per second
 * </ul>
 *
 * <p>The final I/O activity score is the equal mean of those available activity signals.
 */
public final class IoAnalyzer {

  private static final double STORAGE_ACTIVITY_WEIGHT =
      WeightedEvidenceScore.requireNonNegativeWeight(
          "analysis.io.storage-activity.weight",
          ConfigLoader.getDouble("analysis.io.storage-activity.weight", 1.0));

  private static final double SYSCALL_ACTIVITY_WEIGHT =
      WeightedEvidenceScore.requireNonNegativeWeight(
          "analysis.io.syscall-activity.weight",
          ConfigLoader.getDouble("analysis.io.syscall-activity.weight", 1.0));

  private IoAnalyzer() {}

  public static AnalysisResult analyze(List<ProcessHistorySample> samples) {
    List<IoInterval> intervals = intervals(samples);

    if (intervals.isEmpty()) {
      return unavailable();
    }

    SeriesAnalysis storage =
        analyzeSeries(intervals.stream().mapToDouble(IoInterval::storageBytesPerSecond).toArray());
    SeriesAnalysis syscalls =
        analyzeSeries(intervals.stream().mapToDouble(IoInterval::syscallsPerSecond).toArray());

    long readCharacters = intervals.stream().mapToLong(IoInterval::readCharacters).sum();
    long writeCharacters = intervals.stream().mapToLong(IoInterval::writeCharacters).sum();
    long readBytes = intervals.stream().mapToLong(IoInterval::readBytes).sum();
    long writeBytes = intervals.stream().mapToLong(IoInterval::writeBytes).sum();
    long readSyscalls = intervals.stream().mapToLong(IoInterval::readSyscalls).sum();
    long writeSyscalls = intervals.stream().mapToLong(IoInterval::writeSyscalls).sum();

    double averageReadPayload =
        readSyscalls <= 0L ? 0.0 : readCharacters / (double) readSyscalls;
    double averageWritePayload =
        writeSyscalls <= 0L ? 0.0 : writeCharacters / (double) writeSyscalls;

    double storageReadRatio =
        readCharacters <= 0L ? 0.0 : clamp01(readBytes / (double) readCharacters);
    double storageWriteRatio =
        writeCharacters <= 0L ? 0.0 : clamp01(writeBytes / (double) writeCharacters);

    List<EvidenceSignal> evidence =
        List.of(
            EvidenceSignal.available(
                "storage-io-activity",
                storage.activity(),
                "Sustained physical storage throughput relative to this process's recent window."),
            EvidenceSignal.available(
                "syscall-io-activity",
                syscalls.activity(),
                "Sustained read/write syscall rate relative to this process's recent window."));

    double activity =
        WeightedEvidenceScore.calculate(
            WeightedEvidenceScore.weighted(evidence.get(0), STORAGE_ACTIVITY_WEIGHT),
            WeightedEvidenceScore.weighted(evidence.get(1), SYSCALL_ACTIVITY_WEIGHT));

    Map<String, Double> metrics =
        Map.ofEntries(
            Map.entry("latestThroughputBytesPerSecond", storage.latest()),
            Map.entry("averageThroughputBytesPerSecond", storage.mean()),
            Map.entry("peakThroughputBytesPerSecond", storage.peak()),
            Map.entry("storagePersistencePercent", storage.persistence() * 100.0),
            Map.entry("latestSyscallsPerSecond", syscalls.latest()),
            Map.entry("averageSyscallsPerSecond", syscalls.mean()),
            Map.entry("peakSyscallsPerSecond", syscalls.peak()),
            Map.entry("syscallPersistencePercent", syscalls.persistence() * 100.0),
            Map.entry("persistencePercent", mean(storage.persistence(), syscalls.persistence()) * 100.0),
            Map.entry("averageReadBytesPerSyscall", averageReadPayload),
            Map.entry("averageWriteBytesPerSyscall", averageWritePayload),
            Map.entry("storageReadRatioPercent", storageReadRatio * 100.0),
            Map.entry("storageWriteRatioPercent", storageWriteRatio * 100.0),
            Map.entry("intervalsAnalyzed", (double) intervals.size()));

    List<String> reasons =
        List.of(
            String.format(
                "Storage I/O is at %.0f%% of its recent peak with %.0f%% persistence.",
                storage.intensity() * 100.0, storage.persistence() * 100.0),
            String.format(
                "I/O syscall rate is at %.0f%% of its recent peak with %.0f%% persistence.",
                syscalls.intensity() * 100.0, syscalls.persistence() * 100.0),
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

  private static SeriesAnalysis analyzeSeries(double[] values) {
    if (values.length == 0) {
      return SeriesAnalysis.EMPTY;
    }

    double total = 0.0;
    double peak = 0.0;

    for (double value : values) {
      double nonNegative = Math.max(0.0, value);
      total += nonNegative;
      peak = Math.max(peak, nonNegative);
    }

    double mean = total / values.length;
    double latest = Math.max(0.0, values[values.length - 1]);
    double persistence = peak <= 0.0 ? 0.0 : clamp01(mean / peak);
    double intensity = peak <= 0.0 ? 0.0 : clamp01(latest / peak);
    double activity = clamp01(intensity * persistence);

    return new SeriesAnalysis(mean, peak, latest, persistence, intensity, activity);
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
          Duration.between(previous.timestamp(), current.timestamp()).toNanos()
              / 1_000_000_000.0;

      if (seconds <= 0.0) {
        continue;
      }

      long readBytes = nonNegativeDelta(previous.readBytes(), current.readBytes());
      long writeBytes = nonNegativeDelta(previous.writeBytes(), current.writeBytes());
      long readCharacters = nonNegativeDelta(previous.readCharacters(), current.readCharacters());
      long writeCharacters =
          nonNegativeDelta(previous.writeCharacters(), current.writeCharacters());
      long readSyscalls = nonNegativeDelta(previous.readSyscalls(), current.readSyscalls());
      long writeSyscalls = nonNegativeDelta(previous.writeSyscalls(), current.writeSyscalls());

      result.add(
          new IoInterval(
              (readBytes + writeBytes) / seconds,
              (readSyscalls + writeSyscalls) / seconds,
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

  private static double mean(double first, double second) {
    return (clamp01(first) + clamp01(second)) / 2.0;
  }

  private static AnalysisResult unavailable() {
    return new AnalysisResult(
        "io",
        "Sustained I/O activity",
        0.0,
        List.of(
            EvidenceSignal.unavailable(
                "storage-io-activity", "Insufficient process I/O history."),
            EvidenceSignal.unavailable(
                "syscall-io-activity", "Insufficient process I/O history.")),
        Map.of(),
        List.of("Insufficient process I/O history to evaluate recent activity."));
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private record SeriesAnalysis(
      double mean,
      double peak,
      double latest,
      double persistence,
      double intensity,
      double activity) {
    private static final SeriesAnalysis EMPTY =
        new SeriesAnalysis(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
  }

  private record IoInterval(
      double storageBytesPerSecond,
      double syscallsPerSecond,
      long readCharacters,
      long writeCharacters,
      long readSyscalls,
      long writeSyscalls,
      long readBytes,
      long writeBytes) {}
}
