import React from 'react';
import { Icon } from '@blueprintjs/core';
import type { IconName } from '@blueprintjs/icons';
import type {
  AggregatorSnapshot,
} from '../../models/snapshot';
import type { JvmNode } from '../../context/MonitoringContext';
import { getJvmHealth } from '../../utils/health';

export type PageId =
  | 'overview'
  | 'pods'
  | 'resources'
  | 'history'
  | 'problems'
  | 'jvm';

interface NavItem {
  id: Exclude<PageId, 'jvm'>;
  label: string;
  icon: IconName;
}

const navItems: NavItem[] = [
  {
    id: 'overview',
    label: 'Overview',
    icon: 'dashboard',
  },
  {
    id: 'pods',
    label: 'Pods & JVMs',
    icon: 'applications',
  },
  {
    id: 'resources',
    label: 'Resources',
    icon: 'pulse',
  },
  {
    id: 'history',
    label: 'Historical Analysis',
    icon: 'timeline-line-chart',
  },
  {
    id: 'problems',
    label: 'Problems',
    icon: 'warning-sign',
  },
];

export function Sidebar({
  page,
  snapshots,
  jvms,
  selectedJvmKey,
  onNavigate,
  onOpenJvm,
}: {
  page: PageId;
  snapshots: AggregatorSnapshot[];
  jvms: JvmNode[];
  selectedJvmKey: string | null;
  onNavigate: (page: Exclude<PageId, 'jvm'>) => void;
  onOpenJvm: (key: string) => void;
}) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-mark">AH</div>
        <div>
          <div className="brand-name">A-Haythorus</div>
          <div className="brand-caption">JVM observability</div>
        </div>
      </div>

      <nav className="primary-nav">
        {navItems.map((item) => (
          <button
            key={item.id}
            className={`nav-item ${
              page === item.id ? 'nav-item-active' : ''
            }`}
            onClick={() => onNavigate(item.id)}
          >
            <Icon icon={item.icon} size={17} />
            <span>{item.label}</span>

            {item.id === 'pods' ? (
              <span className="nav-count">{snapshots.length}</span>
            ) : null}
          </button>
        ))}
      </nav>

      <div className="sidebar-section">
        <div className="sidebar-section-title">
          Live JVMs
          <span>{jvms.length}</span>
        </div>

        <div className="sidebar-jvms">
          {jvms.map((node) => {
            const health = getJvmHealth(node.snapshot);

            return (
              <button
                key={node.key}
                className={`sidebar-jvm ${
                  selectedJvmKey === node.key && page === 'jvm'
                    ? 'sidebar-jvm-active'
                    : ''
                }`}
                onClick={() => onOpenJvm(node.key)}
              >
                <span
                  className={`sidebar-health-dot sidebar-health-${health.toLowerCase()}`}
                />

                <span className="sidebar-jvm-copy">
                  <strong>{node.pod.app}</strong>
                  <small>
                    {node.pod.name} · PID {node.snapshot.pid}
                  </small>
                </span>
              </button>
            );
          })}

          {jvms.length === 0 ? (
            <div className="sidebar-empty">
              Waiting for JVMs…
            </div>
          ) : null}
        </div>
      </div>

      <div className="sidebar-footer">
        <span className="live-indicator" />
        Auto-refresh · 5s
      </div>
    </aside>
  );
}
