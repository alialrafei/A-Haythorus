import { API } from './endpoints';
import { getJson } from './httpClient';
import type {
  AggregatorSnapshot,
  ClassHistogramEntry,
  DeadlockResponse,
  GcSnapshot,
  JvmDeltaSnapshot,
  JvmHistoryResponse,
  JvmSnapshot,
  MemoryPoolSnapshot,
  MemorySnapshot,
  RootMetadata,
  ThreadCountResponse,
  ThreadDumpSnapshot,
  TimestampResponse,
} from '../models/snapshot';

type SnapshotEnvelope =
  | AggregatorSnapshot
  | AggregatorSnapshot[]
  | {
      members?: AggregatorSnapshot[];
      snapshots?: AggregatorSnapshot[];
    };

function normalizeSnapshots(response: SnapshotEnvelope): AggregatorSnapshot[] {
  if (Array.isArray(response)) {
    return response;
  }

  if ('jvmSnapshots' in response) {
    return [response];
  }

  return response.members ?? response.snapshots ?? [];
}

function withShard(path: string, shard: number | null | undefined): string {
  if (shard == null) {
    return path;
  }

  return `${path}?shard=${encodeURIComponent(shard)}`;
}

export const sidecarApi = {
  getRoot(signal?: AbortSignal) {
    return getJson<RootMetadata>(API.root, signal);
  },

  async getSnapshots(
    shard?: number | null,
    signal?: AbortSignal,
  ) {
    const response = await getJson<SnapshotEnvelope>(
      withShard(API.snapshot, shard),
      signal,
    );
    return normalizeSnapshots(response);
  },

  getHistories(shard?: number | null, signal?: AbortSignal) {
    return getJson<JvmHistoryResponse[]>(
      withShard(API.history, shard),
      signal,
    );
  },

  getJvms(signal?: AbortSignal) {
    return getJson<JvmSnapshot[]>(API.jvms, signal);
  },

  getJvm(pid: number, signal?: AbortSignal) {
    return getJson<JvmSnapshot>(API.jvm(pid), signal);
  },

  getMemory(pid: number, signal?: AbortSignal) {
    return getJson<MemorySnapshot>(API.memory(pid), signal);
  },

  getMemoryPools(pid: number, signal?: AbortSignal) {
    return getJson<MemoryPoolSnapshot[]>(API.memoryPools(pid), signal);
  },

  getGc(pid: number, signal?: AbortSignal) {
    return getJson<GcSnapshot[]>(API.gc(pid), signal);
  },

  getHistogram(pid: number, signal?: AbortSignal) {
    return getJson<ClassHistogramEntry[]>(API.histogram(pid), signal);
  },

  getThreads(pid: number, signal?: AbortSignal) {
    return getJson<ThreadDumpSnapshot>(API.threads(pid), signal);
  },

  getThreadCount(pid: number, signal?: AbortSignal) {
    return getJson<ThreadCountResponse>(API.threadCount(pid), signal);
  },

  getThreadCpuTimes(pid: number, signal?: AbortSignal) {
    return getJson<Record<string, number>>(API.threadCpuTimes(pid), signal);
  },

  getAnalysis(pid: number, signal?: AbortSignal) {
    return getJson<JvmDeltaSnapshot>(API.analysis(pid), signal);
  },

  getJvmHistory(pid: number, signal?: AbortSignal) {
    return getJson<JvmHistoryResponse>(API.jvmHistory(pid), signal);
  },

  getDeadlocks(pid: number, signal?: AbortSignal) {
    return getJson<DeadlockResponse>(API.deadlocks(pid), signal);
  },

  getTimestamp(pid: number, signal?: AbortSignal) {
    return getJson<TimestampResponse>(API.timestamp(pid), signal);
  },
};
