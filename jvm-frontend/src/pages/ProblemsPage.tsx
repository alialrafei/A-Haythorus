import React, { useEffect, useState } from 'react';
import { Icon } from '@blueprintjs/core';
import { useMonitoring } from '../context/MonitoringContext';
import { EmptyState } from '../components/common/EmptyState';
import { StatusBadge } from '../components/common/StatusBadge';
import { sidecarApi } from '../api/sidecarApi';
import type { SavedEvidence, SavedNotification } from '../models/snapshot';
import type { HealthLevel } from '../utils/health';
import { normalizeSeverity } from '../utils/health';
import { toEpochMillis } from '../utils/format';

export function ProblemsPage({ onOpenJvm }: { onOpenJvm: (key: string) => void; }) {
  const { selectedShard, sharding } = useMonitoring();
  const [notifications, setNotifications] = useState<SavedNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedKey, setExpandedKey] = useState<string | null>(null);
  const [evidence, setEvidence] = useState<SavedEvidence[]>([]);
  const [evidenceLoading, setEvidenceLoading] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const shard = sharding.enabled ? selectedShard : null;
        setNotifications(await sidecarApi.getNotifications(shard, controller.signal));
      } catch (cause) {
        if (controller.signal.aborted) return;
        setError(cause instanceof Error ? cause.message : 'Unable to load persisted notifications.');
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };
    void load();
    return () => controller.abort();
  }, [selectedShard, sharding.enabled]);

  const toggleEvidence = async (notification: SavedNotification) => {
    const key = notificationKey(notification);
    if (expandedKey === key) { setExpandedKey(null); return; }
    setExpandedKey(key);
    setEvidenceLoading(true);
    try {
      setEvidence(await sidecarApi.getNotificationEvidence(notification.id, notification.namespace, notification.pod, notification.pid));
    } catch {
      setEvidence([]);
    } finally {
      setEvidenceLoading(false);
    }
  };

  if (loading) return <EmptyState icon="time" title="Loading findings" description="Loading persisted JVM findings." />;
  if (error) return <EmptyState icon="error" title="Unable to load findings" description={error} />;
  if (notifications.length === 0) return <EmptyState icon="tick-circle" title="No recorded findings" description="No notifications have been generated for the selected shard." />;

  return (
    <div className="page-stack">
      <section className="toolbar-panel">
        <div><span className="eyebrow">Notifications</span><h2>Problems & findings</h2><p>Persisted findings produced by the JVM analysis engine.</p></div>
        <div className="problem-count">{notifications.length} notifications</div>
      </section>
      <section className="problem-list">
        {notifications.map((notification) => {
          const level: HealthLevel = normalizeSeverity(notification.severity);
          const key = notificationKey(notification);
          const latest = notification.instances?.length ? toEpochMillis(notification.instances[notification.instances.length - 1]) : null;
          return (
            <article key={key} className="problem-row">
              <div className="problem-icon"><Icon icon={level === 'CRITICAL' || level === 'HIGH' ? 'warning-sign' : 'lightbulb'} size={18} /></div>
              <div className="problem-copy">
                <div className="problem-title-row"><strong>{notification.message}</strong><StatusBadge level={level} compact /></div>
                <p>{notification.id}</p>
                <span>{notification.namespace}/{notification.pod} · JVM {notification.pid} · {notification.instances?.length ?? 0} occurrence(s){latest != null ? ' · ' + new Date(latest).toLocaleString() : ''}</span>
                {expandedKey === key && <div className="problem-evidence">{evidenceLoading ? <span>Loading evidence…</span> : evidence.length === 0 ? <span>No persisted evidence available.</span> : evidence.map((item) => <div key={toEpochMillis(item.timestamp)}><strong>{item.message}</strong><pre>{JSON.stringify(item.payload, null, 2)}</pre></div>)}</div>}
              </div>
              <div className="problem-actions">
                <button type="button" className="button button-secondary" onClick={() => void toggleEvidence(notification)}>{expandedKey === key ? 'Hide evidence' : 'Evidence'}</button>
                <button type="button" className="button button-secondary" onClick={() => onOpenJvm(notification.namespace + '/' + notification.pod + ':' + notification.pid)}><Icon icon="chevron-right" size={16} /></button>
              </div>
            </article>
          );
        })}
      </section>
    </div>
  );
}

function notificationKey(notification: SavedNotification): string {
  return notification.namespace + '/' + notification.pod + ':' + notification.pid + ':' + notification.id;
}