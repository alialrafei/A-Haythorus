package com.acorp.jvminsight.snapshotcollection;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JvmDataStore {
  private static final Map<Long, JvmSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

  private JvmDataStore() {}

  public static void put(long pid, JvmSnapshot jvmSnapshot) {
    SNAPSHOTS.put(pid, jvmSnapshot);
  }

  public static Map<Long, JvmSnapshot> getDateStored() {
    return SNAPSHOTS;
  }

  public static JvmSnapshot getSnapshot(long pid) {
    return SNAPSHOTS.get(pid);
  }
}
