import React from 'react';
import { Icon } from '@blueprintjs/core';
import { MetricCard } from '../components/common/MetricCard';
import { useMonitoring } from '../context/MonitoringContext';
import { formatBytes, formatPercent, formatScore } from '../utils/format';

export function ProcPage({ onOpenJvm }: { onOpenJvm: (key: string) => void }) {
  const { jvms } = useMonitoring();

  const totalReadRate = jvms.reduce(
    (sum, node) => sum + (node.snapshot.delta?.ioDelta?.readBytesPerSecond ?? 0),
    0,
  );
  const totalWriteRate = jvms.reduce(
    (sum, node) => sum + (node.snapshot.delta?.ioDelta?.writeBytesPerSecond ?? 0),
    0,
  );
  const totalRead = jvms.reduce(
    (sum, node) => sum + (node.snapshot.processIo?.readBytes ?? 0),
    0,
  );
  const totalWrite = jvms.reduce(
    (sum, node) => sum + (node.snapshot.processIo?.writeBytes ?? 0),
    0,
  );
  const totalResident = jvms.reduce(
    (sum, node) => sum + (node.snapshot.processMemory?.residentBytes ?? 0),
    0,
  );
  const totalShared = jvms.reduce(
    (sum, node) => sum + (node.snapshot.processMemory?.sharedResidentBytes ?? 0),
    0,
  );
  const avgCpu = jvms.length === 0
    ? 0
    : jvms.reduce((sum, node) => sum + (node.snapshot.processCpu?.processCpuLoad ?? 0), 0) / jvms.length;
  const avgCpuPressure = jvms.length === 0
    ? 0
    : jvms.reduce((sum, node) => sum + (node.snapshot.delta?.cpuAnalysis?.score ?? 0), 0) / jvms.length;
  const avgIoActivity = jvms.length === 0
    ? 0
    : jvms.reduce((sum, node) => sum + (node.snapshot.delta?.ioAnalysis?.score ?? 0), 0) / jvms.length;

  return (
    <div className="page-stack">
      <section className="toolbar-panel">
        <div>
          <span className="eyebrow">Linux process telemetry</span>
          <h2>/proc process view</h2>
          <p>
            Runtime-neutral CPU, I/O, and process-memory measurements collected from the monitored
            Linux processes.
          </p>
        </div>
        <div className="problem-count">{jvms.length} processes</div>
      </section>

      <section className="metric-grid">
        <MetricCard label="Process CPU" value={formatPercent(avgCpu * 100)} detail="Current process CPU load" icon="dashboard" accent="accent" />
        <MetricCard label="CPU pressure" value={formatScore(avgCpuPressure)} detail="Backend process CPU analysis" icon="pulse" />
        <MetricCard label="I/O activity" value={formatScore(avgIoActivity)} detail="Backend process I/O analysis" icon="exchange" />
        <MetricCard label="Read throughput" value={`${formatBytes(totalReadRate)}/s`} detail={`cumulative ${formatBytes(totalRead)}`} icon="download" />
        <MetricCard label="Write throughput" value={`${formatBytes(totalWriteRate)}/s`} detail={`cumulative ${formatBytes(totalWrite)}`} icon="upload" />
        <MetricCard label="Resident memory" value={formatBytes(totalResident)} detail="RSS reported by Linux /proc" icon="memory" />
        <MetricCard label="Shared resident" value={formatBytes(totalShared)} detail="Shared pages from Linux /proc" icon="layers" />
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Per-process snapshot</span>
            <h3>Linux process telemetry</h3>
          </div>
          <span className="panel-meta">/proc + process CPU</span>
        </div>

        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>Application</th>
                <th>Pod</th>
                <th>PID</th>
                <th>CPU</th>
                <th>Read / s</th>
                <th>Write / s</th>
                <th>Read bytes</th>
                <th>Write bytes</th>
                <th>RSS</th>
                <th>Shared</th>
                <th>Anonymous</th>
                <th>File-backed</th>
              </tr>
            </thead>
            <tbody>
              {jvms.map((node) => {
                const snapshot = node.snapshot;
                const io = snapshot.delta?.ioDelta;
                const memory = snapshot.processMemory;
                return (
                  <tr key={node.key} onClick={() => onOpenJvm(node.key)} style={{ cursor: 'pointer' }}>
                    <td><strong>{node.pod.app}</strong></td>
                    <td><span className="mono-cell">{node.pod.namespace}/{node.pod.name}</span></td>
                    <td>{snapshot.pid}</td>
                    <td>{formatPercent((snapshot.processCpu?.processCpuLoad ?? 0) * 100)}</td>
                    <td>{formatBytes(io?.readBytesPerSecond ?? 0)}/s</td>
                    <td>{formatBytes(io?.writeBytesPerSecond ?? 0)}/s</td>
                    <td>{formatBytes(snapshot.processIo?.readBytes ?? 0)}</td>
                    <td>{formatBytes(snapshot.processIo?.writeBytes ?? 0)}</td>
                    <td>{formatBytes(memory?.residentBytes ?? 0)}</td>
                    <td>{formatBytes(memory?.sharedResidentBytes ?? 0)}</td>
                    <td>{formatBytes(memory?.anonymousResidentBytes ?? 0)}</td>
                    <td>{formatBytes(memory?.fileResidentBytes ?? 0)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {jvms.length === 0 ? (
          <div className="inline-empty">
            <Icon icon="info-sign" size={16} /> Waiting for process telemetry…
          </div>
        ) : null}
      </section>

      <section className="split-grid">
        {jvms.map((node) => {
          const memory = node.snapshot.processMemory;
          const io = node.snapshot.processIo;
          const cpu = node.snapshot.processCpu;
          return (
            <section className="panel" key={`${node.key}-detail`}>
              <div className="panel-heading">
                <div>
                  <span className="eyebrow">{node.pod.namespace}</span>
                  <h3>{node.pod.name}</h3>
                </div>
                <span className="panel-meta">PID {node.snapshot.pid}</span>
              </div>
              <div className="delta-grid">
                <div><span>Process CPU</span><strong>{formatPercent((cpu?.processCpuLoad ?? 0) * 100)}</strong><small>current load</small></div>
                <div><span>RSS</span><strong>{formatBytes(memory?.residentBytes ?? 0)}</strong><small>resident set</small></div>
                <div><span>Shared</span><strong>{formatBytes(memory?.sharedResidentBytes ?? 0)}</strong><small>shared resident</small></div>
                <div><span>Anonymous</span><strong>{formatBytes(memory?.anonymousResidentBytes ?? 0)}</strong><small>anonymous resident</small></div>
                <div><span>File-backed</span><strong>{formatBytes(memory?.fileResidentBytes ?? 0)}</strong><small>file resident</small></div>
                <div><span>Stacks</span><strong>{formatBytes(memory?.allThreadStacksBytes ?? 0)}</strong><small>all thread stacks</small></div>
                <div><span>Read</span><strong>{formatBytes(io?.readBytes ?? 0)}</strong><small>cumulative /proc I/O</small></div>
                <div><span>Write</span><strong>{formatBytes(io?.writeBytes ?? 0)}</strong><small>cumulative /proc I/O</small></div>
              </div>
            </section>
          );
        })}
      </section>
    </div>
  );
}
