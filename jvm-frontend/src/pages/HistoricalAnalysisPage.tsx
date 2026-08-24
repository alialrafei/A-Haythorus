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
  JvmHistoryResponse,
  JvmHistorySample,
  JvmSnapshot,
  RootMetadata,
} from '../models/snapshot';
import { MetricCard } from '../components/common/MetricCard';
import {
  formatBytes,
  formatPercent,
  toEpochMillis,
} from '../utils/format';

const REFRESH_MS = 5_000;

export function HistoricalAnalysisPage() {
  const [root, setRoot] = useState<RootMetadata | null>(null);
  const [jvm, setJvm] = useState<JvmSnapshot | null>(null);
  const [history, setHistory] = useState<JvmHistoryResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function refresh() {
      try {
        const [nextRoot, nextJvms] = await Promise.all([
          sidecarApi.getRoot(),
          sidecarApi.getJvms(),
        ]);

        if (cancelled) return;

        setRoot(nextRoot);

        const localJvm = nextJvms[0] ?? null;
        setJvm(localJvm);

        if (!localJvm) {
          setHistory(null);
          return;
        }

        const nextHistory = await sidecarApi.getHistory(localJvm.pid);

        if (!cancelled) {
          setHistory(nextHistory);
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

  const samples = history?.history ?? [];
  const summary = useMemo(() => summarize(samples), [samples]);

  return (
    <div className="page-stack">
      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Historical source</span>
            <h3>Backend-retained JVM history</h3>
          </div>
          <span className="panel-meta">
            {root ? `${root.pod.namespace}/${root.pod.name}` : 'Loading sidecar…'}
            {jvm ? ` · PID ${jvm.pid}` : ''}
          </span>
        </div>

        <p>
          Each sidecar monitors one target JVM, so the UI automatically uses that local JVM and
          fetches <code>/api/v1/jvms/{'{pid}'}/history</code>. No PID selection is required.
        </p>

        {error ? <div className="stale-banner">{error}</div> : null}
      </section>

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

          {jvm?.delta ? (
            <div className="delta-grid">
              <div>
                <span>Heap</span>
                <strong>{formatPercent(jvm.delta.heapGrowthPercentage)}</strong>
                <small>{formatBytes(jvm.delta.heapDelta)}</small>
              </div>
              <div>
                <span>Non-heap</span>
                <strong>{formatPercent(jvm.delta.nonHeapGrowthPercentage)}</strong>
                <small>{formatBytes(jvm.delta.nonHeapDelta)}</small>
              </div>
              <div>
                <span>Threads</span>
                <strong>
                  {jvm.delta.threadDelta >= 0 ? '+' : ''}
                  {jvm.delta.threadDelta}
                </strong>
                <small>latest interval only</small>
              </div>
              <div>
                <span>Instant evidence</span>
                <strong>{jvm.delta.instantaneousLeakScore}</strong>
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

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">How to read this page</span>
            <h3>Two analyses, two questions</h3>
          </div>
        </div>
        <div className="delta-grid">
          <div>
            <span>Latest delta</span>
            <strong>2 samples</strong>
            <small>What changed in the most recent collector interval?</small>
          </div>
          <div>
            <span>Historical analysis</span>
            <strong>{samples.length} samples</strong>
            <small>What behavior persisted across the retained time window?</small>
          </div>
        </div>
      </section>
    </div>
  );
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
