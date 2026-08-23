import React, { useMemo } from 'react';
import { MetricCard } from '../components/common/MetricCard';
import { EmptyState } from '../components/common/EmptyState';
import { JvmCard } from '../components/overview/JvmCard';
import {
  useMonitoring,
} from '../context/MonitoringContext';
import {
  formatBytes,
  percentage,
} from '../utils/format';
import {
  getJvmHealth,
  healthRank,
  maxHealth,
} from '../utils/health';
import { StatusBadge } from '../components/common/StatusBadge';

export function OverviewPage({
  onOpenJvm,
}: {
  onOpenJvm: (key: string) => void;
}) {
  const {
    snapshots,
    jvms,
    historyByJvm,
  } = useMonitoring();

  const summary = useMemo(() => {
    const heapUsed = jvms.reduce(
      (total, node) =>
        total + (node.snapshot.memory?.heapUsed ?? 0),
      0,
    );

    const heapMax = jvms.reduce(
      (total, node) =>
        total + Math.max(0, node.snapshot.memory?.heapMax ?? 0),
      0,
    );

    const threads = jvms.reduce(
      (total, node) => total + node.snapshot.threadCount,
      0,
    );

    const health = maxHealth(
      jvms.map((node) => getJvmHealth(node.snapshot)),
    );

    const problems = jvms.filter(
      (node) =>
        healthRank(getJvmHealth(node.snapshot)) >=
        healthRank('MEDIUM'),
    ).length;

    return {
      heapUsed,
      heapMax,
      threads,
      health,
      problems,
    };
  }, [jvms]);

  if (snapshots.length === 0) {
    return (
      <EmptyState
        icon="cloud"
        title="No sidecar snapshots yet"
        description="A-Haythorus is waiting for the first JVM snapshot from the connected sidecar."
      />
    );
  }

  return (
    <div className="page-stack">
      <section className="hero-panel">
        <div>
          <span className="eyebrow">Cluster posture</span>
          <h2>
            JVM health across {snapshots.length}{' '}
            {snapshots.length === 1 ? 'pod' : 'pods'}
          </h2>
          <p>
            Live runtime signals collected directly from the
            A-Haythorus sidecars.
          </p>
        </div>

        <StatusBadge level={summary.health} />
      </section>

      <section className="metric-grid">
        <MetricCard
          label="Monitored pods"
          value={snapshots.length}
          detail={`${jvms.length} active JVM${jvms.length === 1 ? '' : 's'}`}
          icon="applications"
          accent="accent"
        />

        <MetricCard
          label="Heap in use"
          value={formatBytes(summary.heapUsed)}
          detail={`${percentage(
            summary.heapUsed,
            summary.heapMax,
          ).toFixed(0)}% of ${formatBytes(summary.heapMax)}`}
          icon="database"
        />

        <MetricCard
          label="Live threads"
          value={summary.threads}
          detail="Across all monitored JVMs"
          icon="people"
        />

        <MetricCard
          label="Attention needed"
          value={summary.problems}
          detail={
            summary.problems === 0
              ? 'No medium-or-higher findings'
              : 'Medium, high or critical JVMs'
          }
          icon="warning-sign"
          accent={summary.problems > 0 ? 'warning' : 'good'}
        />
      </section>

      <section className="content-section">
        <div className="section-heading">
          <div>
            <span className="eyebrow">Runtime topology</span>
            <h3>Monitored JVMs</h3>
          </div>
          <span className="section-meta">{jvms.length} live</span>
        </div>

        <div className="jvm-card-grid">
          {jvms.map((node) => (
            <JvmCard
              key={node.key}
              node={node}
              history={historyByJvm[node.key] ?? []}
              onOpen={() => onOpenJvm(node.key)}
            />
          ))}
        </div>
      </section>
    </div>
  );
}
