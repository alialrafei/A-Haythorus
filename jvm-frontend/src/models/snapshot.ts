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

export interface ProcessCpuSnapshot {
  processCpuTimeNanos: number;
  processCpuLoad: number;
  systemCpuLoad: number;
  availableProcessors: number;
}

export interface ProcessIoSnapshot {
  readCharacters: number;
  writeCharacters: number;
  readSyscalls: number;
  writeSyscalls: number;
  readBytes: number;
  writeBytes: number;
  cancelledWriteBytes: number;
}

export interface CpuThreadConsumer {
  threadId: number;
  threadName: string | null;
  cpuTimeDeltaNanos: number;
}

export interface CpuDeltaSnapshot {
  processCpuTimeDeltaNanos: number;
  processCpuUtilizationPercentage: number;
  processCpuLoad: number;
  systemCpuLoad: number;
  availableProcessors: number;
  topConsumers: CpuThreadConsumer[];
}

export interface IoDeltaSnapshot {
  readCharactersDelta: number;
  writeCharactersDelta: number;
  readSyscallsDelta: number;
  writeSyscallsDelta: number;
  readBytesDelta: number;
  writeBytesDelta: number;
  cancelledWriteBytesDelta: number;
  readBytesPerSecond: number;
  writeBytesPerSecond: number;
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
  evidence: string[] | null;
}

export interface EvidenceSignal {
  name: string;
  value: number;
  available: boolean;
  description: string;
}

export interface AnalysisResult {
  domain: string;
  scoreLabel: string;
  score: number;
  evidence: EvidenceSignal[];
  metrics: Record<string, number>;
  reasons: string[];
}

export interface ProcessHistorySample {
  timestamp: TimestampValue;
  cpuTimeNanos: number;
  availableProcessors: number;
  readCharacters: number;
  writeCharacters: number;
  readSyscalls: number;
  writeSyscalls: number;
  readBytes: number;
  writeBytes: number;
}

export interface JvmDeltaSnapshot {
  intervalMillis: number;
  previousHeapUsed: number;
  currentHeapUsed: number;
  heapDelta: number;
  positiveHeapDelta: number;
  reclaimedHeapBytes: number;
  heapGrowthPercentage: number;
  previousNonHeapUsed: number;
  currentNonHeapUsed: number;
  nonHeapDelta: number;
  positiveNonHeapDelta: number;
  reclaimedNonHeapBytes: number;
  nonHeapGrowthPercentage: number;
  previousThreadCount: number;
  currentThreadCount: number;
  threadDelta: number;
  threadGrowthPercentage: number;
  gcDelta: GcDeltaSnapshot[];
  histogramDelta: HistogramDelta[];
  poolDelta: MemoryPoolDelta[];
  histogramPositiveBytes: number;
  histogramReclaimedBytes: number;
  histogramTopClassPositiveBytes: number;
  currentDeadlockCount: number;
  deadlockDelta: number;
  leakSeverity: string | null;
  cpuDelta: CpuDeltaSnapshot | null;
  ioDelta: IoDeltaSnapshot | null;
  cpuAnalysis: AnalysisResult | null;
  ioAnalysis: AnalysisResult | null;
  instantaneousLeakScore: number;
  leakScore: number;
  heapGrowthPersistence: number;
  windowHeapGrowthBytes: number;
  windowGcCollections: number;
  historicalWeight: number;
  leakReasons: string[];
  recommendations: Recommendation[];
  threadCpuTimes: Record<string, number> | null;
}

export interface JvmSnapshot {
  pid: number;
  threadsInfos?: RawThreadInfo[] | null;
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
  processCpu: ProcessCpuSnapshot | null;
  processIo: ProcessIoSnapshot | null;
}

export interface JvmHistorySample {
  timestamp: TimestampValue;
  heapUsed: number;
  nonHeapUsed: number;
  oldGenerationUsed: number;
  threadCount: number;
  gcCollectionCounts: Record<string, number>;
  process: ProcessHistorySample;
  processCpuTimeNanos: number;
  processReadBytes: number;
  processWriteBytes: number;
  leakConfidence: number;
}

export interface JvmHistoryResponse {
  pod: PodInfo;
  pid: number;
  timestamp: TimestampValue;
  history: JvmHistorySample[];
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
  processCpuUtilizationPercentage: number;
  processCpuLoad: number;
  systemCpuLoad: number;
  readBytesPerSecond: number;
  writeBytesPerSecond: number;
}
