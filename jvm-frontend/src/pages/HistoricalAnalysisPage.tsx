import React, { useEffect, useMemo, useState } from 'react';
import { Area, AreaChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { sidecarApi } from '../api/sidecarApi';
import { useMonitoring } from '../context/MonitoringContext';
import type { AggregatorSnapshot, JvmHistoryResponse, JvmHistorySample, JvmSnapshot } from '../models/snapshot';
import { MetricCard } from '../components/common/MetricCard';
import { AnalysisMethodologyPanel } from '../components/analysis/AnalysisMethodologyPanel';
import '../components/analysis/analysisMethodology.css';
import { formatBytes, formatPercent, formatScore, toEpochMillis } from '../utils/format';

const REFRESH_MS = 5_000;

type ProcessHistoryPoint = { timestamp: number; label: string; cpuUtilization: number; readBytesPerSecond: number; writeBytesPerSecond: number };

export function HistoricalAnalysisPage() {
  const { sharding, selectedShard } = useMonitoring();
  const [histories, setHistories] = useState<JvmHistoryResponse[]>([]);
  const [snapshots, setSnapshots] = useState<AggregatorSnapshot[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function refresh() {
      try {
        const shard = sharding.enabled ? selectedShard : null;
        const [nextHistories, nextSnapshots] = await Promise.all([sidecarApi.getHistories(shard), sidecarApi.getSnapshots(shard)]);
        if (!cancelled) { setHistories(nextHistories); setSnapshots(nextSnapshots); setError(null); }
      } catch (cause) {
        if (!cancelled) setError(cause instanceof Error ? cause.message : 'Unable to load JVM history.');
      }
    }
    void refresh();
    const timer = window.setInterval(() => void refresh(), REFRESH_MS);
    return () => { cancelled = true; window.clearInterval(timer); };
  }, [selectedShard, sharding.enabled]);

  return (
    <div className="page-stack">
      <AnalysisMethodologyPanel />
      {error ? <div className="stale-banner">{error}</div> : null}
      {histories.map((history) => <PodHistorySection key={`${history.pod.namespace}/${history.pod.name}:${history.pid}`} history={history} latestJvm={findJvmSnapshot(snapshots, history)} />)}
      {histories.length === 0 ? <section className="panel"><div className="inline-empty">Waiting for JVM history from cluster sidecars…</div></section> : null}
    </div>
  );
}

function PodHistorySection({ history, latestJvm }: { history: JvmHistoryResponse; latestJvm: JvmSnapshot | null }) {
  const samples = history.history ?? [];
  const summary = useMemo(() => summarize(samples), [samples]);
  const processHistory = useMemo(() => buildProcessHistory(samples), [samples]);
  const latestDelta = latestJvm?.delta;
  const latestProcess = processHistory[processHistory.length - 1];
  const cpuAnalysis = latestDelta?.cpuAnalysis;
  const ioAnalysis = latestDelta?.ioAnalysis;

  return (
    <section className="content-section">
      <div className="section-heading">
        <div><span className="eyebrow">{history.pod.namespace}</span><h3>{history.pod.name}</h3></div>
        <span className="section-meta">{history.pod.app} · PID {history.pid} · {samples.length} samples</span>
      </div>

      <section className="metric-grid">
        <MetricCard label="Historical leak confidence" value={formatScore(summary.latestConfidence)} detail={`${samples.length} retained backend samples`} icon="timeline-line-chart" />
        <MetricCard label="Analyzer-window heap change" value={formatBytes(latestJvm?.delta?.windowHeapGrowthBytes ?? summary.netHeapGrowth)} detail="net heap change" icon="database" />
        <MetricCard label="Heap-growth persistence" value={formatPercent((latestJvm?.delta?.heapGrowthPersistence ?? summary.persistence) * 100)} detail="fraction of intervals moving upward" icon="series-search" />
        <MetricCard label="Retained-history old-gen change" value={formatBytes(summary.oldGenGrowth)} detail={`${summary.windowSeconds.toFixed(1)} s retained history`} icon="refresh" />
        <MetricCard label="CPU utilization" value={formatPercent(latestProcess?.cpuUtilization ?? 0)} detail="calculated from retained CPU counters" hint="Process CPU utilization calculated from consecutive backend history samples and visible processor count." icon="dashboard" accent="accent" />
        <MetricCard label="CPU pressure" value={formatScore(cpuAnalysis?.score ?? 0)} detail={cpuAnalysis?.scoreLabel ?? 'Latest CPU analysis unavailable'} hint="Backend CPU analysis for the latest JVM delta." icon="pulse" />
        <MetricCard label="I/O activity" value={formatScore(ioAnalysis?.score ?? 0)} detail={ioAnalysis?.scoreLabel ?? 'Latest I/O analysis unavailable'} hint="Backend I/O analysis for the latest JVM delta." icon="exchange" />
        <MetricCard label="I/O throughput" value={`${formatBytes((latestProcess?.readBytesPerSecond ?? 0) + (latestProcess?.writeBytesPerSecond ?? 0))}/s`} detail={`read ${formatBytes(latestProcess?.readBytesPerSecond ?? 0)}/s · write ${formatBytes(latestProcess?.writeBytesPerSecond ?? 0)}/s`} hint="Process I/O throughput calculated from consecutive backend history counters." icon="download" accent="accent" />
      </section>

      <section className="split-grid">
        <article className="panel panel-large">
          <div className="panel-heading"><div><span className="eyebrow">Historical analysis</span><h3>Heap retention across backend samples</h3></div><span className="panel-meta">{samples.length} samples</span></div>
          <HistoryChart samples={samples} />
        </article>
        <article className="panel panel-large">
          <div className="panel-heading"><div><span className="eyebrow">Process telemetry</span><h3>CPU and I/O across backend samples</h3></div><span className="panel-meta">{Math.max(0, processHistory.length - 1)} intervals</span></div>
          <ProcessChart points={processHistory} />
        </article>
      </section>

      <section className="panel">
        <div className="panel-heading"><div><span className="eyebrow">Latest interval</span><h3>CPU and I/O delta</h3></div></div>
        {latestProcess ? <div className="delta-grid">
          <div><span>CPU</span><strong>{formatPercent(latestProcess.cpuUtilization)}</strong><small>process utilization</small></div>
          <div><span>I/O read</span><strong>{formatBytes(latestProcess.readBytesPerSecond)}/s</strong><small>process throughput</small></div>
          <div><span>I/O write</span><strong>{formatBytes(latestProcess.writeBytesPerSecond)}/s</strong><small>process throughput</small></div>
          <div><span>Total I/O</span><strong>{formatBytes(latestProcess.readBytesPerSecond + latestProcess.writeBytesPerSecond)}/s</strong><small>read + write</small></div>
        </div> : <div className="inline-empty">Waiting for backend history samples…</div>}
      </section>
    </section>
  );
}

function findJvmSnapshot(snapshots: AggregatorSnapshot[], history: JvmHistoryResponse): JvmSnapshot | null {
  const pod = snapshots.find((snapshot) => snapshot.pod?.namespace === history.pod.namespace && snapshot.pod?.name === history.pod.name);
  return pod?.jvmSnapshots?.find((jvm) => jvm.pid === history.pid) ?? null;
}

function buildProcessHistory(samples: JvmHistorySample[]): ProcessHistoryPoint[] {
  return samples.map((sample, index) => {
    const previous = index > 0 ? samples[index - 1] : null;
    const timestamp = toEpochMillis(sample.timestamp);
    const elapsedMillis = Math.max(0, timestamp - (previous ? toEpochMillis(previous.timestamp) : timestamp));
    const elapsedNanos = elapsedMillis * 1_000_000;
    const processors = Math.max(1, sample.process?.availableProcessors ?? 1);
    const cpuDelta = previous ? Math.max(0, sample.process.cpuTimeNanos - previous.process.cpuTimeNanos) : 0;
    const readDelta = previous ? Math.max(0, sample.process.readBytes - previous.process.readBytes) : 0;
    const writeDelta = previous ? Math.max(0, sample.process.writeBytes - previous.process.writeBytes) : 0;
    const seconds = elapsedMillis / 1000;
    return {
      timestamp,
      label: new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
      cpuUtilization: elapsedNanos > 0 ? Math.min(100, (cpuDelta / (elapsedNanos * processors)) * 100) : 0,
      readBytesPerSecond: seconds > 0 ? readDelta / seconds : 0,
      writeBytesPerSecond: seconds > 0 ? writeDelta / seconds : 0,
    };
  });
}

function summarize(samples: JvmHistorySample[]) {
  if (samples.length === 0) return { latestConfidence: 0, netHeapGrowth: 0, oldGenGrowth: 0, persistence: 0, positiveIntervals: 0, intervals: 0, windowSeconds: 0 };
  const first = samples[0];
  const last = samples[samples.length - 1];
  let positiveIntervals = 0;
  for (let index = 1; index < samples.length; index += 1) if (samples[index].heapUsed > samples[index - 1].heapUsed) positiveIntervals += 1;
  const intervals = Math.max(0, samples.length - 1);
  return {
    latestConfidence: last.leakConfidence,
    netHeapGrowth: last.heapUsed - first.heapUsed,
    oldGenGrowth: last.oldGenerationUsed - first.oldGenerationUsed,
    persistence: intervals === 0 ? 0 : positiveIntervals / intervals,
    positiveIntervals,
    intervals,
    windowSeconds: Math.max(0, (toEpochMillis(last.timestamp) - toEpochMillis(first.timestamp)) / 1000),
  };
}

function HistoryChart({ samples }: { samples: JvmHistorySample[] }) {
  const data = samples.map((sample) => ({ ...sample, label: new Date(toEpochMillis(sample.timestamp)).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) }));
  if (data.length < 2) return <div className="chart-empty">Waiting for backend history samples…</div>;
  return <div className="chart-area"><ResponsiveContainer width="100%" height="100%"><AreaChart data={data}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--chart-grid)" /><XAxis dataKey="label" minTickGap={28} tick={{ fill: 'var(--text-muted)', fontSize: 11 }} axisLine={false} tickLine={false} /><YAxis tickFormatter={(value: number) => formatBytes(value)} width={76} tick={{ fill: 'var(--text-muted)', fontSize: 11 }} axisLine={false} tickLine={false} /><Tooltip formatter={(value: number | string) => formatBytes(Number(value))} contentStyle={tooltipStyle} /><Area type="monotone" dataKey="heapUsed" name="Heap used" stroke="var(--accent)" fill="transparent" strokeWidth={2} isAnimationActive={false} /><Area type="monotone" dataKey="oldGenerationUsed" name="Old generation" stroke="var(--cyan)" fill="transparent" strokeWidth={2} isAnimationActive={false} /></AreaChart></ResponsiveContainer></div>;
}

function ProcessChart({ points }: { points: ProcessHistoryPoint[] }) {
  if (points.length < 2) return <div className="chart-empty">Waiting for at least two backend samples to calculate CPU and I/O deltas…</div>;
  return <div className="chart-area"><ResponsiveContainer width="100%" height="100%"><LineChart data={points}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--chart-grid)" /><XAxis dataKey="label" minTickGap={28} tick={{ fill: 'var(--text-muted)', fontSize: 11 }} axisLine={false} tickLine={false} /><YAxis yAxisId="cpu" domain={[0, 100]} tickFormatter={(value: number) => `${value}%`} width={55} tick={{ fill: 'var(--text-muted)', fontSize: 11 }} axisLine={false} tickLine={false} /><YAxis yAxisId="io" orientation="right" tickFormatter={(value: number) => formatBytes(value)} width={70} tick={{ fill: 'var(--text-muted)', fontSize: 11 }} axisLine={false} tickLine={false} /><Tooltip contentStyle={tooltipStyle} /><Line yAxisId="cpu" type="monotone" dataKey="cpuUtilization" name="CPU utilization" stroke="var(--accent)" strokeWidth={2} dot={false} isAnimationActive={false} /><Line yAxisId="io" type="monotone" dataKey="readBytesPerSecond" name="I/O read" stroke="var(--cyan)" strokeWidth={2} dot={false} isAnimationActive={false} /><Line yAxisId="io" type="monotone" dataKey="writeBytesPerSecond" name="I/O write" stroke="var(--text-primary)" strokeWidth={2} dot={false} isAnimationActive={false} /></LineChart></ResponsiveContainer></div>;
}

const tooltipStyle: React.CSSProperties = { border: '1px solid var(--border-strong)', borderRadius: 10, background: 'var(--surface-elevated)', color: 'var(--text-primary)', boxShadow: 'var(--shadow-lg)', fontSize: 12 };
