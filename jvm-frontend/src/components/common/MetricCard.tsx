import React from 'react';
import { Icon } from '@blueprintjs/core';
import type { IconName } from '@blueprintjs/icons';

export function MetricCard({
  label,
  value,
  detail,
  icon,
  accent = 'neutral',
}: {
  label: string;
  value: React.ReactNode;
  detail?: React.ReactNode;
  icon: IconName;
  accent?: 'neutral' | 'good' | 'warning' | 'danger' | 'accent';
}) {
  return (
    <article className={`metric-card metric-${accent}`}>
      <div className="metric-icon">
        <Icon icon={icon} size={18} />
      </div>

      <div className="metric-content">
        <div className="metric-label">{label}</div>
        <div className="metric-value">{value}</div>
        {detail ? <div className="metric-detail">{detail}</div> : null}
      </div>
    </article>
  );
}
