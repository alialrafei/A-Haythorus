import React from 'react';
import { Icon } from '@blueprintjs/core';
import { useMonitoring } from '../context/MonitoringContext';
import { MetricCard } from '../components/common/MetricCard';
import { formatBytes, formatPercent, formatScore } from '../utils/format';

export function ResourcesPage({ onOpenJvm }: { onOpenJvm: (key: string) => void }) {
  const { jvms } = useMonitoring();

  const totalReadRate = jvms.reduce((sum, node) => sum + (node.snapshot.delta?.ioDelta?.readBytesPerSecond ?? 0), 0);
  const totalWriteRate = jvms.reduce((sum, node) => sum + (node.snapshot.delta?.ioDelta?.writeBytesPerSecond ?? 0), 0);
  const totalRead = jvms.reduce((sum, node) => sum + (node.snapshot.processIo?.readBytes ?? 0), 0);
  const totalWrite = jvms.reduce((sum, node) => sum + (node.snapshot.processIo?.writeBytes ?? 0), 0);
  const cpuAnalysis = jvms.map((node) => node.snapshot.delta?.cpuAnalysis).filter((value): value is NonNullable<typeof value> => value != null);
  const ioAnalysis = jvms.map((node) => node.snapshot.delta?.ioAnalysis).filter((value): value is NonNullable<typeof value> => value != null);
  const avgCpuPressure = cpuAnalysis.length === 0 ? 0 : cpuAnalysis.reduce((sum, analysis) => sum + analysis.score, 0) / cpuAnalysis.length;
  const avgIoActivity = ioAnalysis.length === 0 ? 0 : ioAnalysis.reduce((sum, analysis) => sum + analysis.score, 0) / ioAnalysis.length;
  const avgCpuLoad = jvms.length === 0 ? 0 : jvms.reduce((sum, node) => sum + (node.snapshot.processCpu?.processCpuLoad ?? 0), 0) / jvms.length;

  return (
    <div className="page-stack">
      <section className="metric-grid">
        <MetricCard label="Process CPU" value={formatPercent(avgCpuLoad * 100)} detail="Raw JVM process CPU load" icon="dashboard" accent="accent" />
        <MetricCard label="CPU pressure" value={formatScore(avgCpuPressure)} detail="Backend sustained CPU analysis" icon="pulse" />
        <MetricCard label="I/O activity" value={formatScore(avgIoActivity)} detail="Backend sustained I/O analysis" icon="exchange" />
        <MetricCard label="Disk read throughput" value={`${formatBytes(totalReadRate)}/s`} detail={`cumulative ${formatBytes(totalRead)}`} icon="download" />
        <MetricCard label="Disk write throughput" value={`${formatBytes(totalWriteRate)}/s`} detail={`cumulative ${formatBytes(totalWrite)}`} icon="upload" />
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div><span className="eyebrow">Runtime-neutral process analysis</span><h3>CPU and I/O by monitored process</h3></div>
          <span className="panel-meta">{jvms.length} processes</span>
        </div>
        <div className="table-scroll">
          <table className="data-table">
            <thead><tr><th>Application</th><th>Pod</th><th>PID</th><th>Process CPU</th><th>CPU pressure</th><th>I/O activity</th><th>Read / s</th><th>Write / s</th><th>Cumulative read</th><th>Cumulative write</th></tr></thead>
            <tbody>
              {jvms.map((node) => {
                const snapshot = node.snapshot;
                const delta = snapshot.delta;
                const cpu = delta?.cpuDelta;
                const io = delta?.ioDelta;
                const cpuHistorical = delta?.cpuAnalysis;
                const ioHistorical = delta?.ioAnalysis;
                return (
                  <tr key={node.key} onClick={() => onOpenJvm(node.key)} style={{ cursor: 'pointer' }}>
                    <td><strong>{node.pod.app}</strong></td>
                    <td><span className="mono-cell">{node.pod.namespace}/{node.pod.name}</span></td>
                    <td>{snapshot.pid}</td>
                    <td>{formatPercent((snapshot.processCpu?.processCpuLoad ?? 0) * 100)}</td>
                    <td>{formatScore(cpuHistorical?.score ?? 0)}</td>
                    <td>{formatScore(ioHistorical?.score ?? 0)}</td>
                    <td>{formatBytes(io?.readBytesPerSecond ?? 0)}/s</td>
                    <td>{formatBytes(io?.writeBytesPerSecond ?? 0)}/s</td>
                    <td>{formatBytes(snapshot.processIo?.readBytes ?? 0)}</td>
                    <td>{formatBytes(snapshot.processIo?.writeBytes ?? 0)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {jvms.length === 0 ? <div className="inline-empty"><Icon icon="info-sign" size={16} /> Waiting for process resource snapshots…</div> : null}
      </section>
    </div>
  );
}
