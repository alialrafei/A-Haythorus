import type { TimestampValue } from '../models/snapshot';

export function toEpochMillis(value: TimestampValue): number {
  if (typeof value === 'number') {
    return value;
  }

  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? Date.now() : parsed;
}

export function formatTimestamp(value: TimestampValue): string {
  return new Date(toEpochMillis(value)).toLocaleString();
}

export function formatRelativeTime(
  value: TimestampValue | null,
): string {
  if (value === null) {
    return 'Never';
  }

  const delta = Date.now() - toEpochMillis(value);
  const seconds = Math.max(0, Math.floor(delta / 1000));

  if (seconds < 5) return 'just now';
  if (seconds < 60) return `${seconds}s ago`;

  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;

  const hours = Math.floor(minutes / 60);
  return `${hours}h ago`;
}

export function formatBytes(value: number): string {
  if (!Number.isFinite(value)) {
    return '—';
  }

  const absolute = Math.abs(value);
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];

  if (absolute < 1024) {
    return `${value.toFixed(0)} B`;
  }

  const exponent = Math.min(
    Math.floor(Math.log(absolute) / Math.log(1024)),
    units.length - 1,
  );

  const result = value / 1024 ** exponent;

  return `${result.toFixed(result >= 100 ? 0 : result >= 10 ? 1 : 2)} ${units[exponent]}`;
}

export function formatCompact(value: number): string {
  return new Intl.NumberFormat(undefined, {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value);
}

export function formatPercent(value: number): string {
  if (!Number.isFinite(value)) {
    return '0%';
  }

  return `${value.toFixed(Math.abs(value) >= 10 ? 0 : 1)}%`;
}

export function percentage(
  used: number,
  max: number,
): number {
  if (max <= 0) {
    return 0;
  }

  return Math.max(0, Math.min(100, (used / max) * 100));
}

export function formatNanos(value: number): string {
  if (value >= 1_000_000_000) {
    return `${(value / 1_000_000_000).toFixed(2)}s`;
  }

  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}ms`;
  }

  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(1)}µs`;
  }

  return `${value}ns`;
}
