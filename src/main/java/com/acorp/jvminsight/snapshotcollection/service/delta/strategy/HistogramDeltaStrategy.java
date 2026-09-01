package com.acorp.jvminsight.snapshotcollection.service.delta.strategy;

import com.acorp.jvminsight.memory.histogram.ClassHistogramEntry;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.HistogramDelta;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaComputationStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes class-histogram movement between snapshots.
 *
 * <p>Analysis aggregates are calculated from every matched class before UI-oriented top-N
 * truncation. This prevents reclaimed classes from disappearing from the leak-evidence math.
 */
public final class HistogramDeltaStrategy implements DeltaComputationStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(HistogramDeltaStrategy.class);
  private static final int TOP_CLASSES = 50;

  @Override
  public void compute(JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta) {
    if (previous.getHistogram() == null || current.getHistogram() == null) {
      LOGGER.debug("Skipping histogram delta computation.");
      return;
    }

    Map<String, ClassHistogramEntry> previousMap = toMap(previous.getHistogram());
    List<HistogramDelta> deltas = new ArrayList<>();

    long positiveBytes = 0L;
    long reclaimedBytes = 0L;
    long topPositiveBytes = 0L;

    for (ClassHistogramEntry currentEntry : current.getHistogram()) {
      ClassHistogramEntry previousEntry = previousMap.get(currentEntry.getClassName());
      if (previousEntry == null) {
        continue;
      }

      long bytesDelta = currentEntry.getBytes() - previousEntry.getBytes();
      long instancesDelta = currentEntry.getInstances() - previousEntry.getInstances();

      if (bytesDelta == 0L && instancesDelta == 0L) {
        continue;
      }

      if (bytesDelta > 0L) {
        positiveBytes += bytesDelta;
        topPositiveBytes = Math.max(topPositiveBytes, bytesDelta);
      } else if (bytesDelta < 0L) {
        reclaimedBytes += -bytesDelta;
      }

      HistogramDelta histogramDelta = new HistogramDelta();
      histogramDelta.setClassName(currentEntry.getClassName());
      histogramDelta.setPreviousBytes(previousEntry.getBytes());
      histogramDelta.setCurrentBytes(currentEntry.getBytes());
      histogramDelta.setBytesDelta(bytesDelta);
      histogramDelta.setPreviousInstances(previousEntry.getInstances());
      histogramDelta.setCurrentInstances(currentEntry.getInstances());
      histogramDelta.setInstancesDelta(instancesDelta);
      deltas.add(histogramDelta);
    }

    delta.setHistogramPositiveBytes(positiveBytes);
    delta.setHistogramReclaimedBytes(reclaimedBytes);
    delta.setHistogramTopClassPositiveBytes(topPositiveBytes);

    deltas.sort(Comparator.comparingLong(HistogramDelta::getBytesDelta).reversed());
    if (deltas.size() > TOP_CLASSES) {
      deltas = new ArrayList<>(deltas.subList(0, TOP_CLASSES));
    }

    delta.setHistogramDelta(deltas);
    LOGGER.debug("Computed {} histogram deltas.", deltas.size());
  }

  private Map<String, ClassHistogramEntry> toMap(List<ClassHistogramEntry> histogram) {
    Map<String, ClassHistogramEntry> map = new HashMap<>(histogram.size());
    for (ClassHistogramEntry entry : histogram) {
      map.put(entry.getClassName(), entry);
    }
    return map;
  }
}
