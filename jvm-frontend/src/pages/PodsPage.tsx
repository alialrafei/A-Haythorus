import React, { useMemo, useState } from 'react';
import { Icon } from '@blueprintjs/core';
import { useMonitoring } from '../context/MonitoringContext';
import { EmptyState } from '../components/common/EmptyState';
import { StatusBadge } from '../components/common/StatusBadge';
import {
  formatBytes,
  percentage,
} from '../utils/format';
import {
  getJvmHealth,
  maxHealth,
} from '../utils/health';

export function PodsPage({
  onOpenJvm,
}: {
  onOpenJvm: (key: string) => void;
}) {
  const { snapshots, jvms } = useMonitoring();
  const [query, setQuery] = useState('');

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();

    if (!normalized) {
      return snapshots;
    }

    return snapshots.filter((snapshot) => {
      const haystack = [
        snapshot.pod.name,
        snapshot.pod.namespace,
        snapshot.pod.node,
        snapshot.pod.app,
      ]
        .join(' ')
        .toLowerCase();

      return haystack.includes(normalized);
    });
  }, [snapshots, query]);

  if (snapshots.length === 0) {
    return (
      <EmptyState
        icon="applications"
        title="No pods discovered"
        description="No sidecar snapshot has been received yet."
      />
    );
  }

  return (
    <div className="page-stack">
      <section className="toolbar-panel">
        <div>
          <span className="eyebrow">Inventory</span>
          <h2>Pods & JVMs</h2>
        </div>

        <label className="search-box">
          <Icon icon="search" size={16} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search pod, namespace, node or app…"
          />
        </label>
      </section>

      <section className="pod-list">
        {filtered.map((podSnapshot) => {
          const podJvms = jvms.filter(
            (node) =>
              node.pod.name === podSnapshot.pod.name &&
              node.pod.namespace === podSnapshot.pod.namespace,
          );

          const heapUsed = podJvms.reduce(
            (total, node) =>
              total + (node.snapshot.memory?.heapUsed ?? 0),
            0,
          );

          const heapMax = podJvms.reduce(
            (total, node) =>
              total +
              Math.max(0, node.snapshot.memory?.heapMax ?? 0),
            0,
          );

          const threads = podJvms.reduce(
            (total, node) =>
              total + node.snapshot.threadCount,
            0,
          );

          const health = maxHealth(
            podJvms.map((node) =>
              getJvmHealth(node.snapshot),
            ),
          );

          return (
            <article
              key={`${podSnapshot.pod.namespace}/${podSnapshot.pod.name}`}
              className="pod-panel"
            >
              <div className="pod-panel-header">
                <div className="pod-identity">
                  <div className="pod-icon">
                    <Icon icon="cube" size={20} />
                  </div>

                  <div>
                    <h3>{podSnapshot.pod.name}</h3>
                    <p>
                      {podSnapshot.pod.namespace} ·{' '}
                      {podSnapshot.pod.node}
                    </p>
                  </div>
                </div>

                <StatusBadge level={health} />
              </div>

              <div className="pod-metrics">
                <div>
                  <span>Application</span>
                  <strong>{podSnapshot.pod.app}</strong>
                </div>
                <div>
                  <span>JVMs</span>
                  <strong>{podJvms.length}</strong>
                </div>
                <div>
                  <span>Heap</span>
                  <strong>{formatBytes(heapUsed)}</strong>
                </div>
                <div>
                  <span>Threads</span>
                  <strong>{threads}</strong>
                </div>
                <div>
                  <span>Heap utilization</span>
                  <strong>
                    {percentage(heapUsed, heapMax).toFixed(0)}%
                  </strong>
                </div>
              </div>

              <div className="pod-jvms">
                {podJvms.map((node) => (
                  <button
                    key={node.key}
                    className="pod-jvm-row"
                    onClick={() => onOpenJvm(node.key)}
                  >
                    <div>
                      <strong>JVM {node.snapshot.pid}</strong>
                      <span>
                        {formatBytes(
                          node.snapshot.memory?.heapUsed ?? 0,
                        )}{' '}
                        heap · {node.snapshot.threadCount} threads
                      </span>
                    </div>

                    <div className="pod-jvm-row-right">
                      <StatusBadge
                        level={getJvmHealth(node.snapshot)}
                        compact
                      />
                      <Icon icon="chevron-right" size={16} />
                    </div>
                  </button>
                ))}
              </div>
            </article>
          );
        })}
      </section>
    </div>
  );
}
