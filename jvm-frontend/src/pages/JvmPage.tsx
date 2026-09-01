import React, { useMemo, useState } from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Icon } from '@blueprintjs/core';
import type {
  JvmHistoryPoint,
  MemoryPoolSnapshot,
  ThreadDumpThread,
} from '../models/snapshot';
import type { JvmNode } from '../context/MonitoringContext';
import { MetricCard } from '../components/common/MetricCard';
import { StatusBadge } from '../components/common/StatusBadge';
import {
  formatBytes,
  formatCompact,
  formatNanos,
  formatPercent,
  formatScore,
  formatTimestamp,
  percentage,
} from '../utils/format';
import {
  getJvmHealth,
  normalizeSeverity,
} from '../utils/health';

type JvmTab =
  | 'overview'
  | 'memory'
  | 'threads'
  | 'gc'
  | 'objects'
  | 'analysis';

const tabs: Array<{ id: JvmTab; label: string }> = [
  { id: 'overview', label: 'Overview' },
  { id: 'memory', label: 'Memory' },
  { id: 'threads', label: 'Threads' },
  { id: 'gc', label: 'Garbage collection' },
  { id: 'objects', label: 'Objects' },
  { id: 'analysis', label: 'Analysis' },
];

const threadStateColors: Record<string, string> = {
  RUNNABLE: '#6d5dfc',
  BLOCKED: '#ef5350',
  WAITING: '#ffb547',
  TIMED_WAITING: '#38bdf8',
  TERMINATED: '#64748b',
  UNKNOWN: '#94a3b8',
};

export function JvmPage({
  node,
  history,
}: {
  node: JvmNode;
  history: JvmHistoryPoint[];
}) {
  const [tab, setTab] = useState<JvmTab>('overview');

  const health = getJvmHealth(node.snapshot);

  return (
    <div className="page-stack">
      <section className="jvm-hero">
        <div className="jvm-hero-main">
          <div className="jvm-icon-large">
            <Icon icon="pulse" size={23} />
          </div>

          <div>
            <div className="jvm-title-row">
              <h2>
                {node.pod.app} · JVM {node.snapshot.pid}
              </h2>
              <StatusBadge level={health} />
            </div>

            <p>
              {node.pod.namespace}/{node.pod.name} ·{' '}
              {node.pod.node}
            </p>

            <span className="snapshot-time">
              Snapshot {formatTimestamp(node.snapshot.timestamp)}
            </span>
          </div>
        </div>
      </section>

      <div className="tab-strip">
        {tabs.map((item) => (
          <button
            key={item.id}
            className={tab === item.id ? 'tab-active' : ''}
            onClick={() => setTab(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab === 'overview' ? (
        <OverviewTab node={node} history={history} />
      ) : null}

      {tab === 'memory' ? (
        <MemoryTab node={node} history={history} />
      ) : null}

      {tab === 'threads' ? (
        <ThreadsTab node={node} />
      ) : null}

      {tab === 'gc' ? <GcTab node={node} /> : null}

      {tab === 'objects' ? <ObjectsTab node={node} /> : null}

      {tab === 'analysis' ? <AnalysisTab node={node} /> : null}
    </div>
  );
}

function OverviewTab({
  node,
  history,
}: {
  node: JvmNode;
  history: JvmHistoryPoint[];
}) {
  const snapshot = node.snapshot;
  const memory = snapshot.memory;
  const totalGcCollections = snapshot.gc.reduce(
    (total, gc) => total + gc.collectionCount,
    0,
  );

  return (
    <>
      <section className="metric-grid">
        <MetricCard
          label="Heap used"
          value={formatBytes(memory?.heapUsed ?? 0)}
          detail={`${percentage(
            memory?.heapUsed ?? 0,
            memory?.heapMax ?? 0,
          ).toFixed(0)}% utilized`}
          icon="database"
          accent="accent"
        />

        <MetricCard
          label="Threads"
          value={snapshot.threadCount}
          detail={`${snapshot.dumpSnapshot?.summary.blocked ?? 0} blocked`}
          icon="people"
        />

        <MetricCard
          label="GC collections"
          value={formatCompact(totalGcCollections)}
          detail={`${snapshot.gc.length} collectors`}
          icon="refresh"
        />

        <MetricCard
          label="Leak score"
          value={formatScore(snapshot.delta?.leakScore ?? 0)}
          detail={snapshot.delta?.leakSeverity ?? 'No elevated risk'}
          icon="warning-sign"
          accent={
            (snapshot.delta?.leakScore ?? 0) > 50
              ? 'warning'
              : 'good'
          }
        />
      </section>

      <section className="split-grid">
        <article className="panel panel-large">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">Trend</span>
              <h3>Heap utilization</h3>
            </div>

            <span className="panel-meta">
              {history.length} samples
            </span>
          </div>

          <MemoryHistoryChart history={history} />
        </article>

        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">Thread states</span>
              <h3>Current distribution</h3>
            </div>
          </div>

          <ThreadStateDonut
            summary={snapshot.dumpSnapshot?.summary}
          />
        </article>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Memory pools</span>
            <h3>Runtime memory areas</h3>
          </div>
        </div>

        <MemoryPoolTable pools={snapshot.pools} />
      </section>
    </>
  );
}

function MemoryTab({
  node,
  history,
}: {
  node: JvmNode;
  history: JvmHistoryPoint[];
}) {
  const memory = node.snapshot.memory;

  return (
    <>
      <section className="metric-grid">
        <MetricCard
          label="Heap used"
          value={formatBytes(memory?.heapUsed ?? 0)}
          detail={`of ${formatBytes(memory?.heapMax ?? 0)}`}
          icon="database"
          accent="accent"
        />

        <MetricCard
          label="Heap committed"
          value={formatBytes(memory?.heapCommitted ?? 0)}
          detail="Committed to this JVM"
          icon="layers"
        />

        <MetricCard
          label="Non-heap used"
          value={formatBytes(memory?.nonHeapUsed ?? 0)}
          detail={`of ${formatBytes(
            memory?.nonHeapCommitted ?? 0,
          )} committed`}
          icon="properties"
        />

        <MetricCard
          label="Heap growth"
          value={formatPercent(
            node.snapshot.delta?.heapGrowthPercentage ?? 0,
          )}
          detail={formatBytes(
            node.snapshot.delta?.heapDelta ?? 0,
          )}
          icon="timeline-line-chart"
          accent={
            (node.snapshot.delta?.heapGrowthPercentage ?? 0) >
            10
              ? 'warning'
              : 'neutral'
          }
        />
      </section>

      <section className="panel panel-chart">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">History</span>
            <h3>Heap and non-heap usage</h3>
          </div>
        </div>

        <MemoryHistoryChart
          history={history}
          showNonHeap
        />
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Pools</span>
            <h3>Memory pool utilization</h3>
          </div>
        </div>

        <MemoryPoolTable pools={node.snapshot.pools} />
      </section>
    </>
  );
}

function ThreadsTab({
  node,
}: {
  node: JvmNode;
}) {
  const [query, setQuery] = useState('');

  const threads =
    node.snapshot.dumpSnapshot?.threads ?? [];

  const filteredThreads = useMemo(() => {
    const normalized = query.trim().toLowerCase();

    if (!normalized) {
      return threads;
    }

    return threads.filter((thread) =>
      [
        thread.threadName,
        thread.state,
        thread.lockName ?? '',
      ]
        .join(' ')
        .toLowerCase()
        .includes(normalized),
    );
  }, [threads, query]);

  const cpuConsumers =
    node.snapshot.delta?.cpuDelta?.topConsumers ?? [];

  const threadNameById = new Map(
    threads.map((thread) => [
      thread.threadId,
      thread.threadName,
    ]),
  );

  const deadlocks =
    node.snapshot.deadlocks?.length ?? 0;

  return (
    <>
      {deadlocks > 0 ? (
        <section className="critical-banner">
          <Icon icon="warning-sign" size={20} />
          <div>
            <strong>
              {deadlocks} deadlocked thread
              {deadlocks === 1 ? '' : 's'} detected
            </strong>
            <span>
              Inspect the affected threads and lock ownership
              immediately.
            </span>
          </div>
        </section>
      ) : null}

      <section className="split-grid">
        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">States</span>
              <h3>Thread distribution</h3>
            </div>
          </div>

          <ThreadStateDonut
            summary={node.snapshot.dumpSnapshot?.summary}
          />
        </article>

        <article className="panel panel-large">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">CPU</span>
              <h3>Top CPU consumers</h3>
            </div>
          </div>

          <div className="cpu-list">
            {cpuConsumers.slice(0, 8).map((consumer, index) => (
              <div
                className="cpu-row"
                key={`${consumer.threadId}:${index}`}
              >
                <div className="cpu-rank">{index + 1}</div>
                <div className="cpu-name">
                  <strong>
                    {consumer.threadName ??
                      threadNameById.get(
                        consumer.threadId,
                      ) ??
                      `Thread ${consumer.threadId}`}
                  </strong>
                  <span>ID {consumer.threadId}</span>
                </div>
                <strong>
                  {formatNanos(
                    consumer.cpuTimeDeltaNanos,
                  )}
                </strong>
              </div>
            ))}

            {cpuConsumers.length === 0 ? (
              <div className="inline-empty">
                CPU delta is not available for this snapshot.
              </div>
            ) : null}
          </div>
        </article>
      </section>

      <section className="panel">
        <div className="panel-heading panel-heading-with-search">
          <div>
            <span className="eyebrow">Thread dump</span>
            <h3>{threads.length} threads</h3>
          </div>

          <label className="search-box search-box-small">
            <Icon icon="search" size={15} />
            <input
              value={query}
              onChange={(event) =>
                setQuery(event.target.value)
              }
              placeholder="Filter threads…"
            />
          </label>
        </div>

        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>Thread</th>
                <th>ID</th>
                <th>State</th>
                <th>Daemon</th>
                <th>Lock</th>
                <th>Top frame</th>
              </tr>
            </thead>
            <tbody>
              {filteredThreads.map((thread) => (
                <ThreadRow
                  key={thread.threadId}
                  thread={thread}
                />
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}

function ThreadRow({
  thread,
}: {
  thread: ThreadDumpThread;
}) {
  return (
    <tr>
      <td>
        <strong>{thread.threadName}</strong>
      </td>
      <td>{thread.threadId}</td>
      <td>
        <span
          className="thread-state"
          style={{
            ['--thread-color' as string]:
              threadStateColors[thread.state] ??
              threadStateColors.UNKNOWN,
          }}
        >
          {thread.state}
        </span>
      </td>
      <td>{thread.daemon ? 'Yes' : 'No'}</td>
      <td className="mono-cell">
        {thread.lockName ?? '—'}
      </td>
      <td className="stack-cell">
        {thread.stackTrace?.[0] ?? '—'}
      </td>
    </tr>
  );
}

function GcTab({
  node,
}: {
  node: JvmNode;
}) {
  const gc = node.snapshot.gc;

  const totalCollections = gc.reduce(
    (total, item) => total + item.collectionCount,
    0,
  );

  const totalTime = gc.reduce(
    (total, item) => total + item.collectionTimeMillis,
    0,
  );

  const chartData = gc.map((item) => ({
    name: item.name
      .replace('G1 ', '')
      .replace('Generation', 'Gen'),
    collections: item.collectionCount,
    time: item.collectionTimeMillis,
  }));

  return (
    <>
      <section className="metric-grid metric-grid-three">
        <MetricCard
          label="Collections"
          value={formatCompact(totalCollections)}
          detail="Across all collectors"
          icon="refresh"
          accent="accent"
        />
        <MetricCard
          label="Collection time"
          value={`${totalTime} ms`}
          detail="Cumulative JVM time"
          icon="time"
        />
        <MetricCard
          label="Collectors"
          value={gc.length}
          detail={gc.map((item) => item.name).join(' · ')}
          icon="layers"
        />
      </section>

      <section className="panel panel-chart">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Collectors</span>
            <h3>Collection activity</h3>
          </div>
        </div>

        <div className="chart-area">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData}>
              <CartesianGrid
                strokeDasharray="3 3"
                vertical={false}
                stroke="var(--chart-grid)"
              />
              <XAxis
                dataKey="name"
                tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
                axisLine={false}
                tickLine={false}
              />
              <YAxis
                tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip contentStyle={tooltipStyle} />
              <Bar
                dataKey="collections"
                fill="var(--accent)"
                radius={[5, 5, 0, 0]}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="panel">
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>Collector</th>
                <th>Collections</th>
                <th>Total time</th>
                <th>Delta</th>
                <th>Time delta</th>
              </tr>
            </thead>
            <tbody>
              {gc.map((collector) => {
                const delta =
                  node.snapshot.delta?.gcDelta?.find(
                    (item) =>
                      item.gcName === collector.name,
                  );

                return (
                  <tr key={collector.name}>
                    <td>
                      <strong>{collector.name}</strong>
                    </td>
                    <td>{collector.collectionCount}</td>
                    <td>
                      {collector.collectionTimeMillis} ms
                    </td>
                    <td>
                      +{delta?.collectionCountDelta ?? 0}
                    </td>
                    <td>
                      +{delta?.collectionTimeDeltaMillis ?? 0}{' '}
                      ms
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}

function ObjectsTab({
  node,
}: {
  node: JvmNode;
}) {
  const histogram = [...node.snapshot.histogram]
    .sort((left, right) => right.bytes - left.bytes)
    .slice(0, 50);

  const totalBytes = histogram.reduce(
    (total, entry) => total + entry.bytes,
    0,
  );

  const totalInstances = histogram.reduce(
    (total, entry) => total + entry.instances,
    0,
  );

  return (
    <>
      <section className="metric-grid metric-grid-three">
        <MetricCard
          label="Tracked classes"
          value={node.snapshot.histogram.length}
          detail="Current histogram sample"
          icon="heat-grid"
          accent="accent"
        />
        <MetricCard
          label="Tracked instances"
          value={formatCompact(totalInstances)}
          detail="Across displayed classes"
          icon="cube"
        />
        <MetricCard
          label="Tracked bytes"
          value={formatBytes(totalBytes)}
          detail="Across displayed classes"
          icon="database"
        />
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Class histogram</span>
            <h3>Largest retained classes</h3>
          </div>
          <span className="panel-meta">Top 50</span>
        </div>

        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Class</th>
                <th>Instances</th>
                <th>Bytes</th>
                <th>Share</th>
              </tr>
            </thead>
            <tbody>
              {histogram.map((entry, index) => (
                <tr key={`${entry.className}:${index}`}>
                  <td>{index + 1}</td>
                  <td className="mono-cell">
                    <strong>{entry.className}</strong>
                  </td>
                  <td>{formatCompact(entry.instances)}</td>
                  <td>{formatBytes(entry.bytes)}</td>
                  <td>
                    {totalBytes > 0
                      ? `${(
                          (entry.bytes / totalBytes) *
                          100
                        ).toFixed(1)}%`
                      : '0%'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}

function AnalysisTab({
  node,
}: {
  node: JvmNode;
}) {
  const delta = node.snapshot.delta;

  if (!delta) {
    return (
      <section className="panel">
        <div className="inline-empty">
          Analysis is waiting for at least two collector
          snapshots.
        </div>
      </section>
    );
  }

  const severity = normalizeSeverity(delta.leakSeverity);

  return (
    <>
      <section className="analysis-hero">
        <div
          className="score-ring"
          title="EWMA-smoothed JVM memory-retention confidence from 0 to 100. Higher means multiple retention signals agree more strongly; it is not proof of a leak."
        >
          <strong>{formatScore(delta.leakScore)}</strong>
          <span>/ 100</span>
        </div>

        <div className="analysis-hero-copy">
          <span className="eyebrow">Runtime analysis</span>
          <div className="analysis-title-row">
            <h2>
              {severity === 'HEALTHY'
                ? 'No elevated leak signal'
                : `${severity.toLowerCase()} leak signal`}
            </h2>
            <StatusBadge level={severity} />
          </div>
          <p>
            A-Haythorus compares the latest collector interval
            against the previous snapshot and evaluates the
            resulting deltas.
          </p>
        </div>
      </section>

      <section className="metric-grid">
        <MetricCard
          label="CPU pressure"
          value={formatScore(delta.cpuAnalysis?.score ?? 0)}
          detail={delta.cpuAnalysis?.scoreLabel ?? 'Waiting for process history'}
          hint="Weighted historical CPU score from 0 to 100 using normalized utilization and persistence evidence. It describes sustained CPU pressure, not whether CPU usage is inherently bad."
          icon="dashboard"
          accent="accent"
        />
        <MetricCard
          label="CPU persistence"
          value={formatPercent(
            delta.cpuAnalysis?.metrics.persistencePercent ?? 0,
          )}
          hint="Average CPU utilization divided by the recent-window peak. A value near 100% means CPU stayed close to its observed peak instead of appearing as a short spike."
          detail={`avg ${formatPercent(
            delta.cpuAnalysis?.metrics.averageUtilizationPercent ?? 0,
          )} · peak ${formatPercent(
            delta.cpuAnalysis?.metrics.peakUtilizationPercent ?? 0,
          )}`}
          icon="timeline-line-chart"
        />
        <MetricCard
          label="I/O activity"
          value={formatScore(delta.ioAnalysis?.score ?? 0)}
          detail={delta.ioAnalysis?.scoreLabel ?? 'Waiting for process history'}
          hint="Weighted historical I/O activity score from storage-throughput activity and syscall activity. It is relative to this process's recent behavior, not a universal disk-saturation score."
          icon="exchange"
        />
        <MetricCard
          label="I/O persistence"
          value={formatPercent(
            delta.ioAnalysis?.metrics.persistencePercent ?? 0,
          )}
          hint="How consistently storage throughput and syscall activity stayed near their recent-window peaks. Higher means sustained activity rather than one short burst."
          detail={`${formatBytes(
            delta.ioAnalysis?.metrics.latestThroughputBytesPerSecond ?? 0,
          )}/s latest`}
          icon="history"
        />
      </section>

      <section className="split-grid">
        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">CPU analysis</span>
              <h3>Historical process behavior</h3>
            </div>
          </div>

          <div className="evidence-list">
            {delta.cpuAnalysis?.evidence.map((signal) => (
              <div className="evidence-row" key={signal.name}>
                <Icon icon={signal.available ? 'dot' : 'disable'} size={18} />
                <span>
                  <strong>{signal.name}</strong> ·{' '}
                  {signal.available
                    ? formatPercent(signal.value * 100)
                    : 'unavailable'}{' '}
                  · {signal.description}
                </span>
              </div>
            ))}
            {delta.cpuAnalysis?.reasons.map((reason, index) => (
              <div className="evidence-row" key={`cpu-reason:${index}`}>
                <Icon icon="info-sign" size={16} />
                <span>{reason}</span>
              </div>
            ))}
          </div>
        </article>

        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">I/O analysis</span>
              <h3>Historical process behavior</h3>
            </div>
          </div>

          <div className="delta-grid">
            <div>
              <span>Read payload</span>
              <strong>
                {formatBytes(
                  delta.ioAnalysis?.metrics.averageReadBytesPerSyscall ?? 0,
                )}
              </strong>
              <small>average bytes / read syscall</small>
            </div>
            <div>
              <span>Write payload</span>
              <strong>
                {formatBytes(
                  delta.ioAnalysis?.metrics.averageWriteBytesPerSyscall ?? 0,
                )}
              </strong>
              <small>average bytes / write syscall</small>
            </div>
            <div>
              <span>Storage read ratio</span>
              <strong>
                {formatPercent(
                  delta.ioAnalysis?.metrics.storageReadRatioPercent ?? 0,
                )}
              </strong>
              <small>storage bytes / requested bytes</small>
            </div>
            <div>
              <span>Storage write ratio</span>
              <strong>
                {formatPercent(
                  delta.ioAnalysis?.metrics.storageWriteRatioPercent ?? 0,
                )}
              </strong>
              <small>storage bytes / requested bytes</small>
            </div>
          </div>

          <div className="evidence-list">
            {delta.ioAnalysis?.evidence.map((signal) => (
              <div className="evidence-row" key={signal.name}>
                <Icon icon={signal.available ? 'dot' : 'disable'} size={18} />
                <span>
                  <strong>{signal.name}</strong> ·{' '}
                  {signal.available
                    ? formatPercent(signal.value * 100)
                    : 'unavailable'}{' '}
                  · {signal.description}
                </span>
              </div>
            ))}
            {delta.ioAnalysis?.reasons.map((reason, index) => (
              <div className="evidence-row" key={`io-reason:${index}`}>
                <Icon icon="info-sign" size={16} />
                <span>{reason}</span>
              </div>
            ))}
          </div>
        </article>
      </section>

      <section className="split-grid">
        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">Evidence</span>
              <h3>Why this score</h3>
            </div>
          </div>

          <div className="evidence-list">
            {delta.leakReasons?.map((reason, index) => (
              <div className="evidence-row" key={index}>
                <Icon icon="dot" size={18} />
                <span>{reason}</span>
              </div>
            ))}

            {delta.leakReasons?.length === 0 ? (
              <div className="inline-empty">
                No evidence items were produced.
              </div>
            ) : null}
          </div>
        </article>

        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">Latest delta</span>
              <h3>Runtime movement</h3>
            </div>
          </div>

          <div className="delta-grid">
            <div>
              <span>Heap</span>
              <strong>
                {formatPercent(
                  delta.heapGrowthPercentage,
                )}
              </strong>
              <small>{formatBytes(delta.heapDelta)}</small>
            </div>
            <div>
              <span>Non-heap</span>
              <strong>
                {formatPercent(
                  delta.nonHeapGrowthPercentage,
                )}
              </strong>
              <small>
                {formatBytes(delta.nonHeapDelta)}
              </small>
            </div>
            <div>
              <span>Threads</span>
              <strong>
                {delta.threadDelta >= 0 ? '+' : ''}
                {delta.threadDelta}
              </strong>
              <small>
                {formatPercent(
                  delta.threadGrowthPercentage,
                )}
              </small>
            </div>
            <div>
              <span>Deadlocks</span>
              <strong>{delta.currentDeadlockCount}</strong>
              <small>
                delta {delta.deadlockDelta >= 0 ? '+' : ''}
                {delta.deadlockDelta}
              </small>
            </div>
          </div>
        </article>
      </section>

      <section className="content-section">
        <div className="section-heading">
          <div>
            <span className="eyebrow">Actions</span>
            <h3>Recommendations</h3>
          </div>
          <span className="section-meta">
            {delta.recommendations?.length ?? 0}
          </span>
        </div>

        <div className="recommendation-grid">
          {delta.recommendations?.map(
            (recommendation, index) => (
              <article
                className="recommendation-card"
                key={`${recommendation.title}:${index}`}
              >
                <div className="recommendation-header">
                  <Icon icon="lightbulb" size={18} />
                  <StatusBadge
                    level={normalizeSeverity(
                      recommendation.severity,
                    )}
                    compact
                  />
                </div>

                <h4>{recommendation.title}</h4>

                {recommendation.probableCause ? (
                  <>
                    <span>Probable cause</span>
                    <p>{recommendation.probableCause}</p>
                  </>
                ) : null}

                {recommendation.recommendation ? (
                  <>
                    <span>Recommended action</span>
                    <p>{recommendation.recommendation}</p>
                  </>
                ) : null}
              </article>
            ),
          )}
        </div>
      </section>
    </>
  );
}

function MemoryHistoryChart({
  history,
  showNonHeap = false,
}: {
  history: JvmHistoryPoint[];
  showNonHeap?: boolean;
}) {
  const data = history.map((point) => ({
    ...point,
    label: new Date(point.timestamp).toLocaleTimeString(
      [],
      {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      },
    ),
  }));

  if (data.length < 2) {
    return (
      <div className="chart-empty">
        Building history from live 5-second snapshots…
      </div>
    );
  }

  return (
    <div className="chart-area">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data}>
          <defs>
            <linearGradient
              id="heapGradient"
              x1="0"
              y1="0"
              x2="0"
              y2="1"
            >
              <stop
                offset="0%"
                stopColor="var(--accent)"
                stopOpacity={0.32}
              />
              <stop
                offset="100%"
                stopColor="var(--accent)"
                stopOpacity={0}
              />
            </linearGradient>
          </defs>

          <CartesianGrid
            strokeDasharray="3 3"
            vertical={false}
            stroke="var(--chart-grid)"
          />

          <XAxis
            dataKey="label"
            minTickGap={28}
            tick={{
              fill: 'var(--text-muted)',
              fontSize: 11,
            }}
            axisLine={false}
            tickLine={false}
          />

          <YAxis
            tickFormatter={(value: number) =>
              formatBytes(value)
            }
            width={76}
            tick={{
              fill: 'var(--text-muted)',
              fontSize: 11,
            }}
            axisLine={false}
            tickLine={false}
          />

          <Tooltip
            formatter={(value: number | string) =>
              formatBytes(Number(value))
            }
            contentStyle={tooltipStyle}
          />

          <Area
            type="monotone"
            dataKey="heapUsed"
            name="Heap used"
            stroke="var(--accent)"
            fill="url(#heapGradient)"
            strokeWidth={2}
            isAnimationActive={false}
          />

          {showNonHeap ? (
            <Area
              type="monotone"
              dataKey="nonHeapUsed"
              name="Non-heap"
              stroke="var(--cyan)"
              fill="transparent"
              strokeWidth={2}
              isAnimationActive={false}
            />
          ) : null}
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

function ThreadStateDonut({
  summary,
}: {
  summary:
    | {
        runnable: number;
        waiting: number;
        timedWaiting: number;
        blocked: number;
        terminated: number;
        unknown: number;
      }
    | undefined;
}) {
  const data = [
    {
      name: 'Runnable',
      value: summary?.runnable ?? 0,
      color: threadStateColors.RUNNABLE,
    },
    {
      name: 'Blocked',
      value: summary?.blocked ?? 0,
      color: threadStateColors.BLOCKED,
    },
    {
      name: 'Waiting',
      value: summary?.waiting ?? 0,
      color: threadStateColors.WAITING,
    },
    {
      name: 'Timed waiting',
      value: summary?.timedWaiting ?? 0,
      color: threadStateColors.TIMED_WAITING,
    },
  ].filter((item) => item.value > 0);

  const total = data.reduce(
    (sum, item) => sum + item.value,
    0,
  );

  return (
    <div className="donut-layout">
      <div className="donut-chart">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={data}
              dataKey="value"
              nameKey="name"
              innerRadius="66%"
              outerRadius="92%"
              paddingAngle={3}
              stroke="none"
              isAnimationActive={false}
            >
              {data.map((entry) => (
                <Cell
                  key={entry.name}
                  fill={entry.color}
                />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>

        <div className="donut-center">
          <strong>{total}</strong>
          <span>threads</span>
        </div>
      </div>

      <div className="donut-legend">
        {data.map((item) => (
          <div key={item.name}>
            <span
              className="legend-dot"
              style={{ background: item.color }}
            />
            <span>{item.name}</span>
            <strong>{item.value}</strong>
          </div>
        ))}
      </div>
    </div>
  );
}

function MemoryPoolTable({
  pools,
}: {
  pools: MemoryPoolSnapshot[];
}) {
  return (
    <div className="table-scroll">
      <table className="data-table">
        <thead>
          <tr>
            <th>Pool</th>
            <th>Used</th>
            <th>Committed</th>
            <th>Max</th>
            <th>Utilization</th>
          </tr>
        </thead>
        <tbody>
          {pools.map((pool) => {
            const capacity =
              pool.max > 0 ? pool.max : pool.committed;
            const utilization = percentage(
              pool.used,
              capacity,
            );

            return (
              <tr key={pool.name}>
                <td>
                  <strong>{pool.name}</strong>
                </td>
                <td>{formatBytes(pool.used)}</td>
                <td>{formatBytes(pool.committed)}</td>
                <td>
                  {pool.max > 0
                    ? formatBytes(pool.max)
                    : 'Dynamic'}
                </td>
                <td>
                  <div className="table-progress">
                    <div className="progress-track">
                      <div
                        className="progress-fill"
                        style={{
                          width: `${utilization}%`,
                        }}
                      />
                    </div>
                    <span>{utilization.toFixed(0)}%</span>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
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
