import React, { useMemo } from 'react';
import { Icon } from '@blueprintjs/core';
import { useMonitoring } from '../context/MonitoringContext';
import { EmptyState } from '../components/common/EmptyState';
import { StatusBadge } from '../components/common/StatusBadge';
import type { HealthLevel } from '../utils/health';
import {
  getJvmHealth,
  healthRank,
  normalizeSeverity,
} from '../utils/health';

interface Problem {
  key: string;
  jvmKey: string;
  level: HealthLevel;
  title: string;
  description: string;
  pod: string;
  namespace: string;
  pid: number;
}

export function ProblemsPage({
  onOpenJvm,
}: {
  onOpenJvm: (key: string) => void;
}) {
  const { jvms } = useMonitoring();

  const problems = useMemo<Problem[]>(() => {
    const result: Problem[] = [];

    jvms.forEach((node) => {
      const health = getJvmHealth(node.snapshot);

      if ((node.snapshot.deadlocks?.length ?? 0) > 0) {
        result.push({
          key: `${node.key}:deadlock`,
          jvmKey: node.key,
          level: 'CRITICAL',
          title: `${node.snapshot.deadlocks?.length ?? 0} deadlocked threads`,
          description:
            'The JVM reports a live monitor/synchronizer deadlock.',
          pod: node.pod.name,
          namespace: node.pod.namespace,
          pid: node.snapshot.pid,
        });
      }

      if (healthRank(health) >= healthRank('MEDIUM')) {
        result.push({
          key: `${node.key}:health`,
          jvmKey: node.key,
          level: health,
          title: `${health.toLowerCase()} JVM risk`,
          description:
            node.snapshot.delta?.leakReasons?.[0] ??
            'The JVM analysis engine reports elevated runtime risk.',
          pod: node.pod.name,
          namespace: node.pod.namespace,
          pid: node.snapshot.pid,
        });
      }

      node.snapshot.delta?.recommendations?.forEach(
        (recommendation, index) => {
          result.push({
            key: `${node.key}:recommendation:${index}`,
            jvmKey: node.key,
            level: normalizeSeverity(recommendation.severity),
            title: recommendation.title,
            description:
              recommendation.probableCause ??
              recommendation.recommendation ??
              'Runtime analysis recommendation.',
            pod: node.pod.name,
            namespace: node.pod.namespace,
            pid: node.snapshot.pid,
          });
        },
      );
    });

    return result.sort(
      (left, right) =>
        healthRank(right.level) - healthRank(left.level),
    );
  }, [jvms]);

  if (problems.length === 0) {
    return (
      <EmptyState
        icon="tick-circle"
        title="No active findings"
        description="The current JVM snapshots do not contain deadlocks or analysis recommendations."
      />
    );
  }

  return (
    <div className="page-stack">
      <section className="toolbar-panel">
        <div>
          <span className="eyebrow">Analysis</span>
          <h2>Problems & recommendations</h2>
          <p>
            Findings produced by the current JVM delta and rule engine.
          </p>
        </div>

        <div className="problem-count">
          {problems.length} findings
        </div>
      </section>

      <section className="problem-list">
        {problems.map((problem) => (
          <button
            key={problem.key}
            className="problem-row"
            onClick={() => onOpenJvm(problem.jvmKey)}
          >
            <div className="problem-icon">
              <Icon
                icon={
                  problem.level === 'CRITICAL' ||
                  problem.level === 'HIGH'
                    ? 'warning-sign'
                    : 'lightbulb'
                }
                size={18}
              />
            </div>

            <div className="problem-copy">
              <div className="problem-title-row">
                <strong>{problem.title}</strong>
                <StatusBadge level={problem.level} compact />
              </div>

              <p>{problem.description}</p>

              <span>
                {problem.namespace}/{problem.pod} · JVM{' '}
                {problem.pid}
              </span>
            </div>

            <Icon icon="chevron-right" size={16} />
          </button>
        ))}
      </section>
    </div>
  );
}
