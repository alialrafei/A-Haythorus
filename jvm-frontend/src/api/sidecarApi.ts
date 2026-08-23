import { API } from './endpoints';
import { getJson } from './httpClient';
import type {
  AggregatorSnapshot,
  ClassHistogramEntry,
  DeadlockResponse,
  GcSnapshot,
  JvmDeltaSnapshot,
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

export const sidecarApi = {
  getRoot(signal?: AbortSignal) {
    return getJson<RootMetadata>(API.root, signal);
  },

  async getSnapshots(signal?: AbortSignal) {
    const response = await getJson<SnapshotEnvelope>(API.snapshot, signal);
    return normalizeSnapshots(response);
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

  getDeadlocks(pid: number, signal?: AbortSignal) {
    return getJson<DeadlockResponse>(API.deadlocks(pid), signal);
  },

  getTimestamp(pid: number, signal?: AbortSignal) {
    return getJson<TimestampResponse>(API.timestamp(pid), signal);
  },
};
