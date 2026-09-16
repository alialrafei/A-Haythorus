import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { sidecarApi } from '../api/sidecarApi';
import type { AggregatorSnapshot, JvmHistoryPoint, JvmHistorySample, JvmSnapshot, PodInfo, ShardingCapabilities } from '../models/snapshot';
import { buildJvmKey } from '../utils/health';
import { toEpochMillis } from '../utils/format';

const POLL_INTERVAL_MS = 5_000;
const MAX_HISTORY_POINTS = 72;
const DEFAULT_SHARDING: ShardingCapabilities = { enabled: false, shardCount: 1 };

export interface JvmNode { key: string; pod: PodInfo; snapshot: JvmSnapshot; parent: AggregatorSnapshot; }
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
const MonitoringContext = createContext<MonitoringContextValue | null>(null);

function createHistoryPoint(snapshot: JvmSnapshot): JvmHistoryPoint {
  const memory = snapshot.memory;
  const cpu = snapshot.delta?.cpuDelta;
  const io = snapshot.delta?.ioDelta;
  const processMemory = snapshot.processMemory;
  return {
    timestamp: toEpochMillis(snapshot.timestamp),
    heapUsed: memory?.heapUsed ?? 0,
    heapCommitted: memory?.heapCommitted ?? 0,
    heapMax: memory?.heapMax ?? 0,
    nonHeapUsed: memory?.nonHeapUsed ?? 0,
    threadCount: snapshot.threadCount ?? 0,
    leakScore: snapshot.delta?.leakScore ?? 0,
    processCpuUtilizationPercentage: cpu?.processCpuUtilizationPercentage ?? 0,
    processCpuLoad: cpu?.processCpuLoad ?? 0,
    systemCpuLoad: cpu?.systemCpuLoad ?? 0,
    readBytesPerSecond: io?.readBytesPerSecond ?? 0,
    writeBytesPerSecond: io?.writeBytesPerSecond ?? 0,
    processResidentBytes: processMemory?.residentBytes ?? 0,
    processAnonymousResidentBytes: processMemory?.anonymousResidentBytes ?? 0,
    processFileResidentBytes: processMemory?.fileResidentBytes ?? 0,
    processAllThreadStacksBytes: processMemory?.allThreadStacksBytes ?? 0,
    processDirectBufferBytes: processMemory?.directBufferBytes ?? 0,
    processMappedBufferBytes: processMemory?.mappedBufferBytes ?? 0,
  };
}

function createHistoryPoints(samples: JvmHistorySample[]): JvmHistoryPoint[] {
  return samples.map((sample, index) => {
    const previous = index > 0 ? samples[index - 1] : null;
    const timestamp = toEpochMillis(sample.timestamp);
    const previousTimestamp = previous ? toEpochMillis(previous.timestamp) : timestamp;
    const elapsedMillis = timestamp - previousTimestamp;
    const elapsedNanos = elapsedMillis * 1_000_000;
    const processors = Math.max(1, sample.process.availableProcessors || 1);

    const cpuDelta = previous
      ? Math.max(0, sample.process.cpuTimeNanos - previous.process.cpuTimeNanos)
      : 0;
    const cpuUtilization = elapsedNanos > 0
      ? (cpuDelta / (elapsedNanos * processors)) * 100
      : 0;

    const readDelta = previous
      ? Math.max(0, sample.process.readBytes - previous.process.readBytes)
      : 0;
    const writeDelta = previous
      ? Math.max(0, sample.process.writeBytes - previous.process.writeBytes)
      : 0;
    const seconds = elapsedMillis / 1_000;

    const processMemory = sample.processMemory;
    return {
      timestamp,
      heapUsed: sample.heapUsed,
      heapCommitted: 0,
      heapMax: 0,
      nonHeapUsed: sample.nonHeapUsed,
      threadCount: sample.threadCount,
      leakScore: sample.leakConfidence,
      processCpuUtilizationPercentage: cpuUtilization,
      processCpuLoad: 0,
      systemCpuLoad: 0,
      readBytesPerSecond: seconds > 0 ? readDelta / seconds : 0,
      writeBytesPerSecond: seconds > 0 ? writeDelta / seconds : 0,
      processResidentBytes: processMemory?.residentBytes ?? 0,
      processAnonymousResidentBytes: processMemory?.anonymousResidentBytes ?? 0,
      processFileResidentBytes: processMemory?.fileResidentBytes ?? 0,
      processAllThreadStacksBytes: processMemory?.allThreadStacksBytes ?? 0,
      processDirectBufferBytes: processMemory?.directBufferBytes ?? 0,
      processMappedBufferBytes: processMemory?.mappedBufferBytes ?? 0,
    };
  });
}

export function MonitoringProvider({ children }: { children: React.ReactNode }) {
  const [snapshots, setSnapshots] = useState<AggregatorSnapshot[]>([]);
  const [historyByJvm, setHistoryByJvm] = useState<Record<string, JvmHistoryPoint[]>>({});
  const [sharding, setSharding] = useState<ShardingCapabilities>(DEFAULT_SHARDING);
  const [capabilitiesLoaded, setCapabilitiesLoaded] = useState(false);
  const [selectedShard, setSelectedShardState] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<number | null>(null);

  const updateHistory = useCallback((nextSnapshots: AggregatorSnapshot[]) => {
    setHistoryByJvm((previous) => {
      const next = { ...previous };
      nextSnapshots.forEach((podSnapshot) => {
        podSnapshot.jvmSnapshots?.forEach((jvm) => {
          const key = buildJvmKey(podSnapshot.pod.namespace, podSnapshot.pod.name, jvm.pid);
          const point = createHistoryPoint(jvm);
          const current = next[key] ?? [];
          const last = current[current.length - 1];
          if (!last || last.timestamp !== point.timestamp) next[key] = [...current, point].slice(-MAX_HISTORY_POINTS);
        });
      });
      return next;
    });
  }, []);

  const hydrateHistory = useCallback(async (shard: number | null) => {
    const responses = await sidecarApi.getHistories(shard);
    const hydrated: Record<string, JvmHistoryPoint[]> = {};
    responses.forEach((response) => {
      const key = buildJvmKey(response.pod.namespace, response.pod.name, response.pid);
      hydrated[key] = createHistoryPoints(response.history).slice(-MAX_HISTORY_POINTS);
    });
    setHistoryByJvm(hydrated);
  }, []);

  const setSelectedShard = useCallback((shard: number) => {
    if (!sharding.enabled || shard < 0 || shard >= sharding.shardCount) return;
    setSelectedShardState(shard);
    setSnapshots([]);
    setHistoryByJvm({});
    setLoading(true);
  }, [sharding]);

  const refresh = useCallback(async () => {
    if (!capabilitiesLoaded) return;
    setRefreshing(true);
    try {
      const shard = sharding.enabled ? selectedShard : null;
      const nextSnapshots = await sidecarApi.getSnapshots(shard);
      setSnapshots(nextSnapshots);
      updateHistory(nextSnapshots);
      setLastUpdated(Date.now());
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to reach the A-Haythorus sidecar.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [capabilitiesLoaded, selectedShard, sharding.enabled, updateHistory]);

  useEffect(() => {
    let cancelled = false;
    const loadCapabilities = async () => {
      try {
        const metadata = await sidecarApi.getRoot();
        if (cancelled) return;
        const capabilities = metadata.sharding ?? DEFAULT_SHARDING;
        const normalized: ShardingCapabilities = {
          enabled: capabilities.enabled && capabilities.shardCount > 1,
          shardCount: capabilities.enabled && capabilities.shardCount > 1 ? capabilities.shardCount : 1,
        };
        setSharding(normalized);
        setSelectedShardState((current) => Math.min(current, normalized.shardCount - 1));
        setCapabilitiesLoaded(true);
      } catch (cause) {
        if (cancelled) return;
        setError(cause instanceof Error ? cause.message : 'Unable to load A-Haythorus capabilities.');
        setLoading(false);
      }
    };
    void loadCapabilities();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!capabilitiesLoaded) return undefined;
    let cancelled = false;
    const initialize = async () => {
      const shard = sharding.enabled ? selectedShard : null;
      try {
        await hydrateHistory(shard);
        if (cancelled) return;
        await refresh();
      } catch (cause) {
        if (cancelled) return;
        setError(cause instanceof Error ? cause.message : 'Unable to load monitoring history.');
        setLoading(false);
      }
    };
    void initialize();

    const timer = window.setInterval(() => void refresh(), POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [capabilitiesLoaded, selectedShard, sharding.enabled, hydrateHistory, refresh]);

  const jvms = useMemo<JvmNode[]>(() => snapshots.flatMap((podSnapshot) =>
    (podSnapshot.jvmSnapshots ?? []).map((snapshot) => ({
      key: buildJvmKey(podSnapshot.pod.namespace, podSnapshot.pod.name, snapshot.pid),
      pod: podSnapshot.pod,
      snapshot,
      parent: podSnapshot,
    }))), [snapshots]);

  const value = useMemo<MonitoringContextValue>(() => ({ snapshots, jvms, historyByJvm, sharding, selectedShard, setSelectedShard, loading, refreshing, error, lastUpdated, refresh }), [snapshots, jvms, historyByJvm, sharding, selectedShard, setSelectedShard, loading, refreshing, error, lastUpdated, refresh]);
  return <MonitoringContext.Provider value={value}>{children}</MonitoringContext.Provider>;
}

export function useMonitoring(): MonitoringContextValue {
  const context = useContext(MonitoringContext);
  if (!context) throw new Error('useMonitoring must be used inside MonitoringProvider');
  return context;
}
