export const API = {
  root: '/',
  snapshot: '/api/v1/snapshot',
  cluster: '/api/v1/cluster',
  jvms: '/api/v1/jvms',

  jvm: (pid: number) => `/api/v1/jvms/${pid}`,
  memory: (pid: number) => `/api/v1/jvms/${pid}/memory`,
  memoryPools: (pid: number) => `/api/v1/jvms/${pid}/memory-pools`,
  gc: (pid: number) => `/api/v1/jvms/${pid}/gc`,
  histogram: (pid: number) => `/api/v1/jvms/${pid}/histogram`,
  threads: (pid: number) => `/api/v1/jvms/${pid}/threads`,
  threadInfo: (pid: number) => `/api/v1/jvms/${pid}/thread-info`,
  threadCount: (pid: number) => `/api/v1/jvms/${pid}/thread-count`,
  threadCpuTimes: (pid: number) => `/api/v1/jvms/${pid}/thread-cpu-times`,
  analysis: (pid: number) => `/api/v1/jvms/${pid}/analysis`,
  deadlocks: (pid: number) => `/api/v1/jvms/${pid}/deadlocks`,
  timestamp: (pid: number) => `/api/v1/jvms/${pid}/timestamp`,
} as const;
