import type { JvmSnapshot } from '../models/snapshot';

export type HealthLevel =
  | 'HEALTHY'
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH'
  | 'CRITICAL';

const ranks: Record<HealthLevel, number> = {
  HEALTHY: 0,
  LOW: 1,
  MEDIUM: 2,
  HIGH: 3,
  CRITICAL: 4,
};

export function healthRank(level: HealthLevel): number {
  return ranks[level];
}

export function normalizeSeverity(
  value?: string | null,
): HealthLevel {
  const normalized = value?.toUpperCase();

  if (normalized === 'CRITICAL') return 'CRITICAL';
  if (normalized === 'HIGH') return 'HIGH';
  if (normalized === 'MEDIUM' || normalized === 'WARN' || normalized === 'WARNING') {
    return 'MEDIUM';
  }
  if (normalized === 'LOW' || normalized === 'INFO') {
    return 'LOW';
  }

  return 'HEALTHY';
}

export function getJvmHealth(snapshot: JvmSnapshot): HealthLevel {
  if ((snapshot.deadlocks?.length ?? 0) > 0) {
    return 'CRITICAL';
  }

  return normalizeSeverity(snapshot.delta?.leakSeverity);
}

export function maxHealth(levels: HealthLevel[]): HealthLevel {
  return levels.reduce<HealthLevel>(
    (current, level) =>
      healthRank(level) > healthRank(current) ? level : current,
    'HEALTHY',
  );
}

export function buildJvmKey(
  namespace: string,
  podName: string,
  pid: number,
): string {
  return `${namespace}/${podName}:${pid}`;
}
