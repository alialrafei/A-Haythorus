import React from 'react';
import type { HealthLevel } from '../../utils/health';

export function StatusBadge({
  level,
  compact = false,
}: {
  level: HealthLevel;
  compact?: boolean;
}) {
  const label = level === 'HEALTHY' ? 'Healthy' : level;

  return (
    <span
      className={`status-badge status-${level.toLowerCase()} ${
        compact ? 'status-compact' : ''
      }`}
    >
      <span className="status-dot" />
      {label}
    </span>
  );
}
