package com.acorp.jvminsight.snapshotcollection;

import com.acorp.jvminsight.config.ConfigLoader;
import com.acorp.jvminsight.snapshotcollection.dto.JvmHistorySample;
import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory JVM state store.
 *
 * <p>The latest full snapshot is retained for the REST API and pairwise delta computation. Historical
 * state is stored as lightweight {@link JvmHistorySample} records so thread dumps and class
 * histograms are not duplicated across the whole history window.
 */
public final class JvmDataStore {

  private static final int MAX_HISTORY_SAMPLES =
      Math.max(2, ConfigLoader.getInt("history.max.samples", 120));

  private static final Map<Long, JvmSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
  private static final Map<Long, Deque<JvmHistorySample>> HISTORY = new ConcurrentHashMap<>();

  private JvmDataStore() {}

  /** Stores the latest full snapshot and appends a lightweight historical projection. */
  public static void put(long pid, JvmSnapshot jvmSnapshot) {
    SNAPSHOTS.put(pid, jvmSnapshot);

    Deque<JvmHistorySample> history = HISTORY.computeIfAbsent(pid, ignored -> new ArrayDeque<>());
    synchronized (history) {
      history.addLast(JvmHistorySample.from(jvmSnapshot));
      while (history.size() > MAX_HISTORY_SAMPLES) {
        history.removeFirst();
      }
    }
  }

  public static Map<Long, JvmSnapshot> getDateStored() {
    return SNAPSHOTS;
  }

  public static JvmSnapshot getSnapshot(long pid) {
    return SNAPSHOTS.get(pid);
  }

  /** Returns an immutable point-in-time copy of the retained lightweight history for a PID. */
  public static List<JvmHistorySample> getHistory(long pid) {
    Deque<JvmHistorySample> history = HISTORY.get(pid);
    if (history == null) {
      return List.of();
    }

    synchronized (history) {
      return List.copyOf(history);
    }
  }

  /**
   * Removes the latest snapshot only when it is exactly the object supplied by the caller.
   *
   * <p>This protects against PID reuse and overlapping collectors. History is removed only when
   * the caller successfully removes the latest snapshot it owns.
   */
  public static boolean remove(long pid, JvmSnapshot expectedSnapshot) {
    boolean removed = SNAPSHOTS.remove(pid, expectedSnapshot);
    if (removed) {
      HISTORY.remove(pid);
    }
    return removed;
  }
}
