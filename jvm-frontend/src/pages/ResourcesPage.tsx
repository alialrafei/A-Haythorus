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

  const cpuAnalysis = jvms
    .map((node) => node.snapshot.delta?.cpuAnalysis)
    .filter((value): value is NonNullable<typeof value> => value != null);

  const ioAnalysis = jvms
    .map((node) => node.snapshot.delta?.ioAnalysis)
    .filter((value): value is NonNullable<typeof value> => value != null);

  const avgCpuPressure =
    cpuAnalysis.length === 0
      ? 0
      : cpuAnalysis.reduce((sum, analysis) => sum + analysis.score, 0) /
        cpuAnalysis.length;

  const avgIoActivity =
    ioAnalysis.length === 0
      ? 0
      : ioAnalysis.reduce((sum, analysis) => sum + analysis.score, 0) /
        ioAnalysis.length;

  return (
    <div className="page-stack">
      <section className="metric-grid">
        <MetricCard
          label="CPU pressure"
          value={formatPercent(avgCpuPressure)}
          detail="Historical sustained process pressure"
          icon="dashboard"
          accent="accent"
        />
        <MetricCard
          label="I/O activity"
          value={formatPercent(avgIoActivity)}
          detail="Historical sustained process activity"
          icon="exchange"
        />
        <MetricCard
          label="Disk read throughput"
          value={`${formatBytes(totalReadRate)}/s`}
          detail="Latest interval across monitored processes"
          icon="download"
        />
        <MetricCard
          label="Disk write throughput"
          value={`${formatBytes(totalWriteRate)}/s`}
          detail="Latest interval across monitored processes"
          icon="upload"
        />
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Runtime-neutral process analysis</span>
            <h3>CPU and I/O by monitored process</h3>
          </div>
          <span className="panel-meta">{jvms.length} processes</span>
        </div>

        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>Application</th>
                <th>Pod</th>
                <th>PID</th>
                <th>CPU pressure</th>
                <th>CPU avg</th>
                <th>CPU persistence</th>
                <th>I/O activity</th>
                <th>I/O persistence</th>
                <th>Avg read/syscall</th>
                <th>Avg write/syscall</th>
                <th>Read / s</th>
                <th>Write / s</th>
              </tr>
            </thead>
            <tbody>
              {jvms.map((node) => {
                const delta = node.snapshot.delta;
                const cpu = delta?.cpuDelta;
                const io = delta?.ioDelta;
                const cpuHistorical = delta?.cpuAnalysis;
                const ioHistorical = delta?.ioAnalysis;

                return (
                  <tr
                    key={node.key}
                    onClick={() => onOpenJvm(node.key)}
                    style={{ cursor: 'pointer' }}
                  >
                    <td>
                      <strong>{node.pod.app}</strong>
                    </td>
                    <td>
                      <span className="mono-cell">
                        {node.pod.namespace}/{node.pod.name}
                      </span>
                    </td>
                    <td>{node.snapshot.pid}</td>
                    <td>{formatPercent(cpuHistorical?.score ?? 0)}</td>
                    <td>
                      {formatPercent(
                        cpuHistorical?.metrics.averageUtilizationPercent ??
                          cpu?.processCpuUtilizationPercentage ??
                          0,
                      )}
                    </td>
                    <td>
                      {formatPercent(
                        cpuHistorical?.metrics.persistencePercent ?? 0,
                      )}
                    </td>
                    <td>{formatPercent(ioHistorical?.score ?? 0)}</td>
                    <td>
                      {formatPercent(
                        ioHistorical?.metrics.persistencePercent ?? 0,
                      )}
                    </td>
                    <td>
                      {formatBytes(
                        ioHistorical?.metrics.averageReadBytesPerSyscall ?? 0,
                      )}
                    </td>
                    <td>
                      {formatBytes(
                        ioHistorical?.metrics.averageWriteBytesPerSyscall ?? 0,
                      )}
                    </td>
                    <td>{formatBytes(io?.readBytesPerSecond ?? 0)}/s</td>
                    <td>{formatBytes(io?.writeBytesPerSecond ?? 0)}/s</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {jvms.length === 0 ? (
          <div className="inline-empty">
            <Icon icon="info-sign" size={16} />
            Waiting for process resource snapshots…
          </div>
        ) : null}
      </section>
    </div>
  );
}
