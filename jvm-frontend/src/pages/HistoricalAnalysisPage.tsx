import React, { useEffect, useMemo, useState } from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { sidecarApi } from '../api/sidecarApi';
import type {
  AggregatorSnapshot,
  JvmHistoryResponse,
  JvmHistorySample,
  JvmSnapshot,
} from '../models/snapshot';
import { MetricCard } from '../components/common/MetricCard';
import {
  formatBytes,
  formatPercent,
  toEpochMillis,
} from '../utils/format';

const REFRESH_MS = 5_000;

export function HistoricalAnalysisPage() {
  const [histories, setHistories] = useState<JvmHistoryResponse[]>([]);
  const [snapshots, setSnapshots] = useState<AggregatorSnapshot[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function refresh() {
      try {
        const [nextHistories, nextSnapshots] = await Promise.all([
          sidecarApi.getHistories(),
          sidecarApi.getSnapshots(),
        ]);

        if (!cancelled) {
          setHistories(nextHistories);
          setSnapshots(nextSnapshots);
          setError(null);
        }
      } catch (cause) {
        if (!cancelled) {
          setError(
            cause instanceof Error
              ? cause.message
              : 'Unable to load JVM history.',
          );
        }
      }
    }

    void refresh();
    const timer = window.setInterval(() => void refresh(), REFRESH_MS);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  return (
    <div className="page-stack">
      {error ? <div className="stale-banner">{error}</div> : null}

      {histories.map((history) => {
        const latestJvm = findJvmSnapshot(snapshots, history);
        return (
          <PodHistorySection
            key={`${history.pod.namespace}/${history.pod.name}:${history.pid}`}
            history={history}
            latestJvm={latestJvm}
          />
        );
      })}

      {histories.length === 0 ? (
        <section className="panel">
          <div className="inline-empty">Waiting for JVM history from cluster sidecars…</div>
        </section>
      ) : null}
    </div>
  );
}

function PodHistorySection({
  history,
  latestJvm,
}: {
  history: JvmHistoryResponse;
  latestJvm: JvmSnapshot | null;
}) {
  const samples = history.history ?? [];
  const summary = useMemo(() => summarize(samples), [samples]);

  return (
    <section className="content-section">
      <div className="section-heading">
        <div>
          <span className="eyebrow">{history.pod.namespace}</span>
          <h3>{history.pod.name}</h3>
        </div>
        <span className="section-meta">
          {history.pod.app} · PID {history.pid} · {samples.length} samples
        </span>
      </div>

      <section className="metric-grid">
        <MetricCard
          label="Historical leak confidence"
          value={summary.latestConfidence}
          detail={`${samples.length} backend samples`}
          icon="timeline-line-chart"
          accent={summary.latestConfidence >= 60 ? 'warning' : 'good'}
        />
        <MetricCard
          label="Window heap movement"
          value={formatBytes(summary.netHeapGrowth)}
          detail={`${summary.windowSeconds.toFixed(1)} s observed`}
          icon="database"
        />
        <MetricCard
          label="Growth persistence"
          value={formatPercent(summary.persistence * 100)}
          detail={`${summary.positiveIntervals}/${summary.intervals} positive intervals`}
          icon="series-search"
        />
        <MetricCard
          label="Old-gen movement"
          value={formatBytes(summary.oldGenGrowth)}
          detail={`${summary.gcCollections} GC collections in window`}
          icon="refresh"
        />
      </section>

      <section className="split-grid">
        <article className="panel panel-large">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">Historical analysis</span>
              <h3>Heap retention across backend samples</h3>
            </div>
            <span className="panel-meta">{samples.length} samples</span>
          </div>
          <HistoryChart samples={samples} />
        </article>

        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">Latest delta · 2 samples</span>
              <h3>Most recent interval</h3>
            </div>
          </div>

          {latestJvm?.delta ? (
            <div className="delta-grid">
              <div>
                <span>Heap</span>
                <strong>{formatPercent(latestJvm.delta.heapGrowthPercentage)}</strong>
                <small>{formatBytes(latestJvm.delta.heapDelta)}</small>
              </div>
              <div>
                <span>Non-heap</span>
                <strong>{formatPercent(latestJvm.delta.nonHeapGrowthPercentage)}</strong>
                <small>{formatBytes(latestJvm.delta.nonHeapDelta)}</small>
              </div>
              <div>
                <span>Threads</span>
                <strong>
                  {latestJvm.delta.threadDelta >= 0 ? '+' : ''}
                  {latestJvm.delta.threadDelta}
                </strong>
                <small>latest interval only</small>
              </div>
              <div>
                <span>Instant evidence</span>
                <strong>{latestJvm.delta.instantaneousLeakScore}</strong>
                <small>before historical smoothing</small>
              </div>
            </div>
          ) : (
            <div className="inline-empty">
              Latest pairwise analysis is waiting for two snapshots.
            </div>
          )}
        </article>
      </section>
    </section>
  );
}

function findJvmSnapshot(
  snapshots: AggregatorSnapshot[],
  history: JvmHistoryResponse,
): JvmSnapshot | null {
  const pod = snapshots.find(
    (snapshot) =>
      snapshot.pod.namespace === history.pod.namespace &&
      snapshot.pod.name === history.pod.name,
  );

  return pod?.jvmSnapshots?.find((jvm) => jvm.pid === history.pid) ?? null;
}

function summarize(samples: JvmHistorySample[]) {
  if (samples.length === 0) {
    return {
      latestConfidence: 0,
      netHeapGrowth: 0,
      oldGenGrowth: 0,
      persistence: 0,
      positiveIntervals: 0,
      intervals: 0,
      windowSeconds: 0,
      gcCollections: 0,
    };
  }

  const first = samples[0];
  const last = samples[samples.length - 1];
  let positiveIntervals = 0;

  for (let index = 1; index < samples.length; index += 1) {
    if (samples[index].heapUsed > samples[index - 1].heapUsed) {
      positiveIntervals += 1;
    }
  }

  const intervals = Math.max(0, samples.length - 1);
  const gcNames = new Set([
    ...Object.keys(first.gcCollectionCounts ?? {}),
    ...Object.keys(last.gcCollectionCounts ?? {}),
  ]);
  let gcCollections = 0;

  gcNames.forEach((name) => {
    gcCollections += Math.max(
      0,
      (last.gcCollectionCounts?.[name] ?? 0) -
        (first.gcCollectionCounts?.[name] ?? 0),
    );
  });

  return {
    latestConfidence: last.leakConfidence,
    netHeapGrowth: last.heapUsed - first.heapUsed,
    oldGenGrowth: last.oldGenerationUsed - first.oldGenerationUsed,
    persistence: intervals === 0 ? 0 : positiveIntervals / intervals,
    positiveIntervals,
    intervals,
    windowSeconds: Math.max(
      0,
      (toEpochMillis(last.timestamp) - toEpochMillis(first.timestamp)) / 1000,
    ),
    gcCollections,
  };
}

function HistoryChart({ samples }: { samples: JvmHistorySample[] }) {
  const data = samples.map((sample) => ({
    ...sample,
    label: new Date(toEpochMillis(sample.timestamp)).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }),
  }));

  if (data.length < 2) {
    return <div className="chart-empty">Waiting for backend history samples…</div>;
  }

  return (
    <div className="chart-area">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data}>
          <CartesianGrid
            strokeDasharray="3 3"
            vertical={false}
            stroke="var(--chart-grid)"
          />
          <XAxis
            dataKey="label"
            minTickGap={28}
            tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            tickFormatter={(value: number) => formatBytes(value)}
            width={76}
            tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip
            formatter={(value: number | string) => formatBytes(Number(value))}
            contentStyle={tooltipStyle}
          />
          <Area
            type="monotone"
            dataKey="heapUsed"
            name="Heap used"
            stroke="var(--accent)"
            fill="transparent"
            strokeWidth={2}
            isAnimationActive={false}
          />
          <Area
            type="monotone"
            dataKey="oldGenerationUsed"
            name="Old generation"
            stroke="var(--cyan)"
            fill="transparent"
            strokeWidth={2}
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

const tooltipStyle: React.CSSProperties = {
  border: '1px solid var(--border-strong)',
  borderRadius: 10,
  background: 'var(--surface-elevated)',
  color: 'var(--text-primary)',
  boxShadow: 'var(--shadow-lg)',
  fontSize: 12,
};
