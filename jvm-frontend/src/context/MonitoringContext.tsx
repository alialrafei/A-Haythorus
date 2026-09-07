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
  ShardingCapabilities,
} from '../models/snapshot';
import { buildJvmKey } from '../utils/health';
import { toEpochMillis } from '../utils/format';

const POLL_INTERVAL_MS = 5_000;
const MAX_HISTORY_POINTS = 72;
const DEFAULT_SHARDING: ShardingCapabilities = {
  enabled: false,
  shardCount: 1,
};

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
  sharding: ShardingCapabilities;
  selectedShard: number;
  setSelectedShard: (shard: number) => void;
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
  const [sharding, setSharding] =
    useState<ShardingCapabilities>(DEFAULT_SHARDING);
  const [capabilitiesLoaded, setCapabilitiesLoaded] = useState(false);
  const [selectedShard, setSelectedShardState] = useState(0);
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

  const setSelectedShard = useCallback(
    (shard: number) => {
      if (!sharding.enabled) {
        return;
      }

      if (shard < 0 || shard >= sharding.shardCount) {
        return;
      }

      setSelectedShardState(shard);
      setSnapshots([]);
      setLoading(true);
    },
    [sharding],
  );

  const refresh = useCallback(async () => {
    if (!capabilitiesLoaded) {
      return;
    }

    setRefreshing(true);

    try {
      const shard = sharding.enabled ? selectedShard : null;
      const nextSnapshots = await sidecarApi.getSnapshots(shard);

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
  }, [
    capabilitiesLoaded,
    selectedShard,
    sharding.enabled,
    updateHistory,
  ]);

  useEffect(() => {
    let cancelled = false;

    const loadCapabilities = async () => {
      try {
        const metadata = await sidecarApi.getRoot();

        if (cancelled) {
          return;
        }

        const capabilities = metadata.sharding ?? DEFAULT_SHARDING;
        const normalized: ShardingCapabilities = {
          enabled: capabilities.enabled && capabilities.shardCount > 1,
          shardCount: capabilities.enabled && capabilities.shardCount > 1
            ? capabilities.shardCount
            : 1,
        };

        setSharding(normalized);
        setSelectedShardState((current) =>
          Math.min(current, normalized.shardCount - 1),
        );
        setCapabilitiesLoaded(true);
      } catch (cause) {
        if (cancelled) {
          return;
        }

        setError(
          cause instanceof Error
            ? cause.message
            : 'Unable to load A-Haythorus capabilities.',
        );
        setLoading(false);
      }
    };

    void loadCapabilities();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!capabilitiesLoaded) {
      return undefined;
    }

    void refresh();

    const timer = window.setInterval(() => {
      void refresh();
    }, POLL_INTERVAL_MS);

    return () => {
      window.clearInterval(timer);
    };
  }, [capabilitiesLoaded, refresh]);

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
      sharding,
      selectedShard,
      setSelectedShard,
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
      sharding,
      selectedShard,
      setSelectedShard,
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
