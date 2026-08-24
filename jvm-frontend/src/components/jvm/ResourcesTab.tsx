import React from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { JvmHistoryPoint } from '../../models/snapshot';
import type { JvmNode } from '../../context/MonitoringContext';
import { MetricCard } from '../common/MetricCard';
import { formatBytes, formatPercent } from '../../utils/format';

export function ResourcesTab({
  node,
  history,
}: {
  node: JvmNode;
  history: JvmHistoryPoint[];
}) {
  const cpu = node.snapshot.delta?.cpuDelta;
  const io = node.snapshot.delta?.ioDelta;
  const rawIo = node.snapshot.processIo;

  return (
    <>
      <section className="metric-grid">
        <MetricCard
          label="Process CPU"
          value={formatPercent(cpu?.processCpuUtilizationPercentage ?? 0)}
          detail={`${cpu?.availableProcessors ?? node.snapshot.processCpu?.availableProcessors ?? 0} processors visible`}
          icon="dashboard"
          accent="accent"
        />

        <MetricCard
          label="System CPU load"
          value={formatPercent((cpu?.systemCpuLoad ?? node.snapshot.processCpu?.systemCpuLoad ?? 0) * 100)}
          detail="Host / container-visible CPU load"
          icon="pulse"
        />

        <MetricCard
          label="Disk read"
          value={`${formatBytes(io?.readBytesPerSecond ?? 0)}/s`}
          detail={`${formatBytes(rawIo?.readBytes ?? 0)} cumulative`}
          icon="download"
        />

        <MetricCard
          label="Disk write"
          value={`${formatBytes(io?.writeBytesPerSecond ?? 0)}/s`}
          detail={`${formatBytes(rawIo?.writeBytes ?? 0)} cumulative`}
          icon="upload"
        />
      </section>

      <section className="split-grid">
        <article className="panel panel-large">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">CPU trend</span>
              <h3>Process utilization</h3>
            </div>
            <span className="panel-meta">{history.length} samples</span>
          </div>

          <ResourceHistoryChart
            history={history}
            mode="cpu"
          />
        </article>

        <article className="panel panel-large">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">I/O trend</span>
              <h3>Storage throughput</h3>
            </div>
            <span className="panel-meta">{history.length} samples</span>
          </div>

          <ResourceHistoryChart
            history={history}
            mode="io"
          />
        </article>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Linux process I/O</span>
            <h3>Cumulative counters</h3>
          </div>
        </div>

        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>Metric</th>
                <th>Current value</th>
                <th>Latest delta</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><strong>Storage bytes read</strong></td>
                <td>{formatBytes(rawIo?.readBytes ?? 0)}</td>
                <td>{formatBytes(io?.readBytesDelta ?? 0)}</td>
              </tr>
              <tr>
                <td><strong>Storage bytes written</strong></td>
                <td>{formatBytes(rawIo?.writeBytes ?? 0)}</td>
                <td>{formatBytes(io?.writeBytesDelta ?? 0)}</td>
              </tr>
              <tr>
                <td><strong>Logical characters read</strong></td>
                <td>{formatBytes(rawIo?.readCharacters ?? 0)}</td>
                <td>{formatBytes(io?.readCharactersDelta ?? 0)}</td>
              </tr>
              <tr>
                <td><strong>Logical characters written</strong></td>
                <td>{formatBytes(rawIo?.writeCharacters ?? 0)}</td>
                <td>{formatBytes(io?.writeCharactersDelta ?? 0)}</td>
              </tr>
              <tr>
                <td><strong>Read syscalls</strong></td>
                <td>{rawIo?.readSyscalls ?? 0}</td>
                <td>{io?.readSyscallsDelta ?? 0}</td>
              </tr>
              <tr>
                <td><strong>Write syscalls</strong></td>
                <td>{rawIo?.writeSyscalls ?? 0}</td>
                <td>{io?.writeSyscallsDelta ?? 0}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}

function ResourceHistoryChart({
  history,
  mode,
}: {
  history: JvmHistoryPoint[];
  mode: 'cpu' | 'io';
}) {
  const data = history.map((point) => ({
    ...point,
    label: new Date(point.timestamp).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }),
  }));

  if (data.length < 2) {
    return (
      <div className="chart-empty">
        Building resource history from live snapshots…
      </div>
    );
  }

  const cpuMode = mode === 'cpu';

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
            tickFormatter={(value: number) =>
              cpuMode ? `${value.toFixed(0)}%` : formatBytes(value)
            }
            width={76}
            tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip
            formatter={(value: number | string, name: string) => [
              cpuMode
                ? `${Number(value).toFixed(2)}%`
                : `${formatBytes(Number(value))}/s`,
              name,
            ]}
            contentStyle={tooltipStyle}
          />

          {cpuMode ? (
            <Area
              type="monotone"
              dataKey="processCpuUtilizationPercentage"
              name="Process CPU"
              stroke="var(--accent)"
              fill="transparent"
              strokeWidth={2}
              isAnimationActive={false}
            />
          ) : (
            <>
              <Area
                type="monotone"
                dataKey="readBytesPerSecond"
                name="Read"
                stroke="var(--accent)"
                fill="transparent"
                strokeWidth={2}
                isAnimationActive={false}
              />
              <Area
                type="monotone"
                dataKey="writeBytesPerSecond"
                name="Write"
                stroke="var(--cyan)"
                fill="transparent"
                strokeWidth={2}
                isAnimationActive={false}
              />
            </>
          )}
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
