import React from 'react';
import { Icon } from '@blueprintjs/core';
import type {
  JvmHistoryPoint,
} from '../../models/snapshot';
import type { JvmNode } from '../../context/MonitoringContext';
import { StatusBadge } from '../common/StatusBadge';
import { Sparkline } from '../common/Sparkline';
import {
  formatBytes,
  percentage,
} from '../../utils/format';
import { getJvmHealth } from '../../utils/health';

export function JvmCard({
  node,
  history,
  onOpen,
}: {
  node: JvmNode;
  history: JvmHistoryPoint[];
  onOpen: () => void;
}) {
  const { snapshot, pod } = node;
  const memory = snapshot.memory;
  const heapPercent = percentage(
    memory?.heapUsed ?? 0,
    memory?.heapMax ?? 0,
  );
  const health = getJvmHealth(snapshot);

  return (
    <button className="jvm-card" onClick={onOpen}>
      <div className="jvm-card-header">
        <div>
          <div className="jvm-card-title-row">
            <span className="jvm-card-title">
              JVM {snapshot.pid}
            </span>
            <StatusBadge level={health} compact />
          </div>
          <div className="jvm-card-subtitle">
            {pod.app} · {pod.name}
          </div>
        </div>

        <Icon icon="chevron-right" size={18} />
      </div>

      <div className="jvm-card-chart">
        <Sparkline
          values={history.map((point) => point.heapUsed)}
          tone={
            health === 'CRITICAL' || health === 'HIGH'
              ? 'danger'
              : health === 'MEDIUM'
                ? 'warning'
                : 'accent'
          }
        />
      </div>

      <div className="jvm-card-grid">
        <div>
          <span>Heap</span>
          <strong>{formatBytes(memory?.heapUsed ?? 0)}</strong>
        </div>
        <div>
          <span>Utilization</span>
          <strong>{heapPercent.toFixed(0)}%</strong>
        </div>
        <div>
          <span>Threads</span>
          <strong>{snapshot.threadCount}</strong>
        </div>
        <div>
          <span>Leak score</span>
          <strong>{snapshot.delta?.leakScore ?? 0}</strong>
        </div>
      </div>

      <div className="progress-track">
        <div
          className="progress-fill"
          style={{ width: `${heapPercent}%` }}
        />
      </div>
    </button>
  );
}
