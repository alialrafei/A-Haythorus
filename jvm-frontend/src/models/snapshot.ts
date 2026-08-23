export type TimestampValue = string | number;

export interface PodInfo {
  name: string;
  namespace: string;
  node: string;
  app: string;
}

export interface RootMetadata {
  application: string;
  version: string;
  timestamp: TimestampValue;
  pod: PodInfo;
  monitoredJvmCount: number;
}

export interface AggregatorSnapshot {
  pod: PodInfo;
  time: TimestampValue;
  jvmSnapshots: JvmSnapshot[];
}

export interface MemorySnapshot {
  timestamp: TimestampValue;
  heapUsed: number;
  heapCommitted: number;
  heapMax: number;
  nonHeapUsed: number;
  nonHeapCommitted: number;
}

export interface GcSnapshot {
  name: string;
  collectionCount: number;
  collectionTimeMillis: number;
}

export interface MemoryPoolSnapshot {
  name: string;
  used: number;
  committed: number;
  max: number;
}

export interface ClassHistogramEntry {
  className: string;
  instances: number;
  bytes: number;
}

export interface ThreadDumpSummary {
  runnable: number;
  waiting: number;
  timedWaiting: number;
  blocked: number;
  terminated: number;
  unknown: number;
}

export interface ThreadDumpThread {
  threadName: string;
  threadId: number;
  priority: number;
  daemon: boolean;
  inNative: boolean;
  state: string;
  lockName: string | null;
  lockOwnerId: number | null;
  lockOwnerName: string | null;
  stackTrace: string[];
}

export interface ThreadDumpSnapshot {
  threads: ThreadDumpThread[];
  summary: ThreadDumpSummary;
}

export interface RawThreadInfo {
  threadName?: string;
  threadId?: number;
  threadState?: string;
  [key: string]: unknown;
}

export interface CpuThreadConsumer {
  threadId: number;
  threadName: string | null;
  cpuTimeDeltaNanos: number;
}

export interface CpuDeltaSnapshot {
  topConsumers: CpuThreadConsumer[];
}

export interface GcDeltaSnapshot {
  gcName: string;
  previousCollectionCount: number;
  currentCollectionCount: number;
  collectionCountDelta: number;
  collectionCountGrowthPercentage: number;
  previousCollectionTimeMillis: number;
  currentCollectionTimeMillis: number;
  collectionTimeDeltaMillis: number;
  collectionTimeGrowthPercentage: number;
}

export interface MemoryPoolDelta {
  poolName: string;
  previousUsed: number;
  currentUsed: number;
  usedDelta: number;
  usedGrowthPercentage: number;
  previousCommitted: number;
  currentCommitted: number;
  committedDelta: number;
  committedGrowthPercentage: number;
  previousMax: number;
  currentMax: number;
  maxDelta: number;
}

export interface HistogramDelta {
  className: string;
  previousBytes?: number;
  currentBytes?: number;
  bytesDelta?: number;
  previousInstances?: number;
  currentInstances?: number;
  instancesDelta?: number;
}

export interface Recommendation {
  severity: string;
  confidence: number;
  title: string;
  diagnosis: string | null;
  probableCause: string | null;
  recommendation: string | null;
  evidence: string | null;
}

export interface JvmDeltaSnapshot {
  intervalMillis: number;

  previousHeapUsed: number;
  currentHeapUsed: number;
  heapDelta: number;
  heapGrowthPercentage: number;

  previousNonHeapUsed: number;
  currentNonHeapUsed: number;
  nonHeapDelta: number;
  nonHeapGrowthPercentage: number;

  previousThreadCount: number;
  currentThreadCount: number;
  threadDelta: number;
  threadGrowthPercentage: number;

  gcDelta: GcDeltaSnapshot[];
  histogramDelta: HistogramDelta[];
  poolDelta: MemoryPoolDelta[];

  currentDeadlockCount: number;
  deadlockDelta: number;

  leakSeverity: string | null;
  cpuDelta: CpuDeltaSnapshot | null;
  leakScore: number;

  leakReasons: string[];
  recommendations: Recommendation[];
  threadCpuTimes: Record<string, number> | null;
}

export interface JvmSnapshot {
  pid: number;
  threadsInfos: RawThreadInfo[] | null;
  memory: MemorySnapshot | null;
  gc: GcSnapshot[];
  pools: MemoryPoolSnapshot[];
  histogram: ClassHistogramEntry[];
  timestamp: TimestampValue;
  delta: JvmDeltaSnapshot | null;
  deadlocks: number[] | null;
  threadCount: number;
  threadCpuTimes: Record<string, number>;
  dumpSnapshot: ThreadDumpSnapshot | null;
}

export interface ThreadCountResponse {
  pid: number;
  threadCount: number;
  timestamp: TimestampValue;
}

export interface DeadlockResponse {
  pid: number;
  count: number;
  threadIds: number[];
  threads: RawThreadInfo[] | null;
  timestamp: TimestampValue;
}

export interface TimestampResponse {
  pid: number;
  timestamp: TimestampValue;
}

export interface JvmHistoryPoint {
  timestamp: number;
  heapUsed: number;
  heapCommitted: number;
  heapMax: number;
  nonHeapUsed: number;
  threadCount: number;
  leakScore: number;
}
