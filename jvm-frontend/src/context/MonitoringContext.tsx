import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { sidecarApi } from '../api/sidecarApi';
import type {
  AggregatorSnapshot,
  JvmHistoryPoint,
  JvmSnapshot,
  PodInfo,
} from '../models/snapshot';
import { buildJvmKey } from '../utils/health';
import { toEpochMillis } from '../utils/format';

const POLL_INTERVAL_MS = 5_000;
const MAX_HISTORY_POINTS = 72;

export interface JvmNode {
  key: string;
  pod: PodInfo;
  snapshot: JvmSnapshot;
  parent: AggregatorSnapshot;
}

interface MonitoringContextValue {
  snapshots: AggregatorSnapshot[];
  jvms: JvmNode[];
  historyByJvm: Record<string, JvmHistoryPoint[]>;
  loading: boolean;
  refreshing: boolean;
  error: string | null;
  lastUpdated: number | null;
  refresh: () => Promise<void>;
}

const MonitoringContext =
  createContext<MonitoringContextValue | null>(null);

function createHistoryPoint(snapshot: JvmSnapshot): JvmHistoryPoint {
  const memory = snapshot.memory;
  const cpu = snapshot.delta?.cpuDelta;
  const io = snapshot.delta?.ioDelta;

  return {
    timestamp: toEpochMillis(snapshot.timestamp),
    heapUsed: memory?.heapUsed ?? 0,
    heapCommitted: memory?.heapCommitted ?? 0,
    heapMax: memory?.heapMax ?? 0,
    nonHeapUsed: memory?.nonHeapUsed ?? 0,
    threadCount: snapshot.threadCount ?? 0,
    leakScore: snapshot.delta?.leakScore ?? 0,
    processCpuUtilizationPercentage:
      cpu?.processCpuUtilizationPercentage ?? 0,
    processCpuLoad: cpu?.processCpuLoad ?? 0,
    systemCpuLoad: cpu?.systemCpuLoad ?? 0,
    readBytesPerSecond: io?.readBytesPerSecond ?? 0,
    writeBytesPerSecond: io?.writeBytesPerSecond ?? 0,
  };
}

export function MonitoringProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [snapshots, setSnapshots] = useState<AggregatorSnapshot[]>([]);
  const [historyByJvm, setHistoryByJvm] =
    useState<Record<string, JvmHistoryPoint[]>>({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<number | null>(null);

  const updateHistory = useCallback(
    (nextSnapshots: AggregatorSnapshot[]) => {
      setHistoryByJvm((previous) => {
        const next = { ...previous };

        nextSnapshots.forEach((podSnapshot) => {
          podSnapshot.jvmSnapshots?.forEach((jvm) => {
            const key = buildJvmKey(
              podSnapshot.pod.namespace,
              podSnapshot.pod.name,
              jvm.pid,
            );

            const point = createHistoryPoint(jvm);
            const current = next[key] ?? [];
            const last = current[current.length - 1];

            if (!last || last.timestamp !== point.timestamp) {
              next[key] = [...current, point].slice(-MAX_HISTORY_POINTS);
            }
          });
        });

        return next;
      });
    },
    [],
  );

  const refresh = useCallback(async () => {
    setRefreshing(true);

    try {
      const nextSnapshots = await sidecarApi.getSnapshots();

      setSnapshots(nextSnapshots);
      updateHistory(nextSnapshots);
      setLastUpdated(Date.now());
      setError(null);
    } catch (cause) {
      const message =
        cause instanceof Error
          ? cause.message
          : 'Unable to reach the A-Haythorus sidecar.';

      setError(message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [updateHistory]);

  useEffect(() => {
    void refresh();

    const timer = window.setInterval(() => {
      void refresh();
    }, POLL_INTERVAL_MS);

    return () => {
      window.clearInterval(timer);
    };
  }, [refresh]);

  const jvms = useMemo<JvmNode[]>(
    () =>
      snapshots.flatMap((podSnapshot) =>
        (podSnapshot.jvmSnapshots ?? []).map((snapshot) => ({
          key: buildJvmKey(
            podSnapshot.pod.namespace,
            podSnapshot.pod.name,
            snapshot.pid,
          ),
          pod: podSnapshot.pod,
          snapshot,
          parent: podSnapshot,
        })),
      ),
    [snapshots],
  );

  const value = useMemo<MonitoringContextValue>(
    () => ({
      snapshots,
      jvms,
      historyByJvm,
      loading,
      refreshing,
      error,
      lastUpdated,
      refresh,
    }),
    [
      snapshots,
      jvms,
      historyByJvm,
      loading,
      refreshing,
      error,
      lastUpdated,
      refresh,
    ],
  );

  return (
    <MonitoringContext.Provider value={value}>
      {children}
    </MonitoringContext.Provider>
  );
}

export function useMonitoring(): MonitoringContextValue {
  const context = useContext(MonitoringContext);

  if (!context) {
    throw new Error(
      'useMonitoring must be used inside MonitoringProvider',
    );
  }

  return context;
}
