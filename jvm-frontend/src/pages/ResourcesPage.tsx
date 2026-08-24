import React from 'react';
import { Icon } from '@blueprintjs/core';
import { useMonitoring } from '../context/MonitoringContext';
import { MetricCard } from '../components/common/MetricCard';
import { formatBytes, formatPercent } from '../utils/format';

export function ResourcesPage({
  onOpenJvm,
}: {
  onOpenJvm: (key: string) => void;
}) {
  const { jvms } = useMonitoring();

  const totalReadRate = jvms.reduce(
    (sum, node) => sum + (node.snapshot.delta?.ioDelta?.readBytesPerSecond ?? 0),
    0,
  );
  const totalWriteRate = jvms.reduce(
    (sum, node) => sum + (node.snapshot.delta?.ioDelta?.writeBytesPerSecond ?? 0),
    0,
  );
  const avgCpu =
    jvms.length === 0
      ? 0
      : jvms.reduce(
          (sum, node) =>
            sum + (node.snapshot.delta?.cpuDelta?.processCpuUtilizationPercentage ?? 0),
          0,
        ) / jvms.length;

  return (
    <div className="page-stack">
      <section className="metric-grid metric-grid-three">
        <MetricCard
          label="Average JVM CPU"
          value={formatPercent(avgCpu)}
          detail={`${jvms.length} monitored JVMs`}
          icon="dashboard"
          accent="accent"
        />
        <MetricCard
          label="Disk read throughput"
          value={`${formatBytes(totalReadRate)}/s`}
          detail="Across monitored JVM processes"
          icon="download"
        />
        <MetricCard
          label="Disk write throughput"
          value={`${formatBytes(totalWriteRate)}/s`}
          detail="Across monitored JVM processes"
          icon="upload"
        />
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Process resources</span>
            <h3>CPU and I/O by JVM</h3>
          </div>
          <span className="panel-meta">{jvms.length} JVMs</span>
        </div>

        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>JVM</th>
                <th>Pod</th>
                <th>PID</th>
                <th>Process CPU</th>
                <th>System CPU</th>
                <th>Read / s</th>
                <th>Write / s</th>
                <th>Storage read</th>
                <th>Storage written</th>
              </tr>
            </thead>
            <tbody>
              {jvms.map((node) => {
                const cpu = node.snapshot.delta?.cpuDelta;
                const io = node.snapshot.delta?.ioDelta;
                const rawIo = node.snapshot.processIo;

                return (
                  <tr key={node.key} onClick={() => onOpenJvm(node.key)} style={{ cursor: 'pointer' }}>
                    <td>
                      <strong>{node.pod.app}</strong>
                    </td>
                    <td>
                      <span className="mono-cell">
                        {node.pod.namespace}/{node.pod.name}
                      </span>
                    </td>
                    <td>{node.snapshot.pid}</td>
                    <td>{formatPercent(cpu?.processCpuUtilizationPercentage ?? 0)}</td>
                    <td>{formatPercent((cpu?.systemCpuLoad ?? 0) * 100)}</td>
                    <td>{formatBytes(io?.readBytesPerSecond ?? 0)}/s</td>
                    <td>{formatBytes(io?.writeBytesPerSecond ?? 0)}/s</td>
                    <td>{formatBytes(rawIo?.readBytes ?? 0)}</td>
                    <td>{formatBytes(rawIo?.writeBytes ?? 0)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {jvms.length === 0 ? (
          <div className="inline-empty">
            <Icon icon="info-sign" size={16} />
            Waiting for JVM resource snapshots…
          </div>
        ) : null}
      </section>
    </div>
  );
}
