import React from 'react';
import { Icon, Tooltip } from '@blueprintjs/core';
import type { IconName } from '@blueprintjs/icons';

export function MetricCard({
  label,
  value,
  detail,
  hint,
  icon,
  accent = 'neutral',
}: {
  label: string;
  value: React.ReactNode;
  detail?: React.ReactNode;
  hint?: React.ReactNode;
  icon: IconName;
  accent?: 'neutral' | 'good' | 'warning' | 'danger' | 'accent';
}) {
  return (
    <article className={`metric-card metric-${accent}`}>
      <div className="metric-icon">
        <Icon icon={icon} size={18} />
      </div>

      <div className="metric-content">
        <div className="metric-label">
          <span>{label}</span>
          {hint ? (
            <Tooltip content={hint} placement="top">
              <span
                aria-label={`About ${label}`}
                style={{ display: 'inline-flex', marginLeft: 6, cursor: 'help' }}
              >
                <Icon icon="info-sign" size={13} />
              </span>
            </Tooltip>
          ) : null}
        </div>
        <div className="metric-value">{value}</div>
        {detail ? <div className="metric-detail">{detail}</div> : null}
      </div>
    </article>
  );
}
