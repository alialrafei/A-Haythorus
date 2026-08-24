package com.acorp.jvminsight.snapshotcollection;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory snapshot store.
 *
 * <p>The latest snapshot remains available through the existing map-based API while a bounded
 * per-PID history is retained for trend analysis. The history is intentionally bounded so the
 * sidecar cannot grow memory without limit.
 */
public final class JvmDataStore {

  /** 120 samples at a 5-second interval is roughly 10 minutes of history. */
  private static final int MAX_HISTORY_SNAPSHOTS = 120;

  private static final Map<Long, JvmSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
  private static final Map<Long, Deque<JvmSnapshot>> HISTORY = new ConcurrentHashMap<>();

  private JvmDataStore() {}

  /** Stores the latest snapshot and appends it to the bounded history window. */
  public static void put(long pid, JvmSnapshot jvmSnapshot) {
    SNAPSHOTS.put(pid, jvmSnapshot);

    Deque<JvmSnapshot> history = HISTORY.computeIfAbsent(pid, ignored -> new ArrayDeque<>());
    synchronized (history) {
      history.addLast(jvmSnapshot);
      while (history.size() > MAX_HISTORY_SNAPSHOTS) {
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

  /** Returns an immutable point-in-time copy of the retained history for a PID. */
  public static List<JvmSnapshot> getHistory(long pid) {
    Deque<JvmSnapshot> history = HISTORY.get(pid);
    if (history == null) {
      return List.of();
    }

    synchronized (history) {
      return List.copyOf(history);
    }
  }

  /**
   * Removes the snapshot only if the value currently stored for this PID is exactly the snapshot
   * supplied by the caller.
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
