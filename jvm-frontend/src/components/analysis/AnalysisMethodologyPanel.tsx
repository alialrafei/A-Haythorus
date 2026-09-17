import React, { useEffect, useState } from 'react';
import { Icon, Spinner } from '@blueprintjs/core';
import { sidecarApi } from '../../api/sidecarApi';
import type {
  AggregatorSnapshot,
  AnalysisResult,
  JvmDeltaSnapshot,
} from '../../models/snapshot';
import type { AnalysisMethodology, MethodologyExample } from '../../models/analysisMethodology';

export function AnalysisMethodologyPanel() {
  const [methodology, setMethodology] = useState<AnalysisMethodology | null>(null);
  const [analysis, setAnalysis] = useState<JvmDeltaSnapshot | null>(null);
  const [open, setOpen] = useState(true);
  const [exampleId, setExampleId] = useState('healthy-gc');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      try {
        const [methodologyData, snapshots] = await Promise.all([
          sidecarApi.getAnalysisMethodology(),
          sidecarApi.getSnapshots(),
        ]);

        if (!cancelled) {
          setMethodology(methodologyData);
          setAnalysis(findLatestAnalysis(snapshots));
          setError(null);
        }
      } catch (cause) {
        if (!cancelled) {
          setError(cause instanceof Error ? cause.message : 'Unable to load analysis guide.');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    if (open) {
      void load();
      const timer = window.setInterval(() => void load(), 5_000);
      return () => {
        cancelled = true;
        window.clearInterval(timer);
      };
    }

    return () => {
      cancelled = true;
    };
  }, [open]);

  const selectedExample = methodology?.examples.find((example) => example.id === exampleId)
    ?? methodology?.examples[0];

  return (
    <section className="panel analysis-methodology">
      <button
        type="button"
        className="analysis-methodology-toggle"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
      >
        <span className="analysis-methodology-title">
          <Icon icon="info-sign" size={16} />
          <span>
            <span className="eyebrow">Analysis guide</span>
            <strong>Understand the signal. Know what to do next.</strong>
          </span>
        </span>
        <Icon icon={open ? 'chevron-up' : 'chevron-down'} size={16} />
      </button>

      {open ? (
        <div className="analysis-methodology-body">
          {loading && !methodology ? (
            <div className="inline-empty"><Spinner size={18} /> Loading analysis guidance…</div>
          ) : error && !methodology ? (
            <div className="inline-empty">{error}</div>
          ) : methodology ? (
            <>
              <ActionableSummary analysis={analysis} />

              <div className="analysis-methodology-intro">
                <strong>How to read the numbers</strong>
                <p>
                  Scores describe the strength of observed behavioral evidence. They are not probabilities.
                  A high score should tell you what deserves investigation; the evidence and recommended
                  next steps below explain why.
                </p>
              </div>

              <div className="analysis-methodology-grid">
                {methodology.sections.map((section) => (
                  <article key={section.id} className="analysis-methodology-card">
                    <h4>{section.title}</h4>
                    <p>{section.description}</p>
                    {section.formula ? (
                      <code className="analysis-formula">{section.formula}</code>
                    ) : null}
                    {section.signals?.length ? (
                      <ul>
                        {section.signals.map((signal) => (
                          <li key={signal.id}>
                            <strong>{signal.name}:</strong> {signal.description}
                          </li>
                        ))}
                      </ul>
                    ) : null}
                    {section.scoreFlow?.length ? (
                      <ul>
                        {section.scoreFlow.map((item) => <li key={item}>{item}</li>)}
                      </ul>
                    ) : null}
                  </article>
                ))}
              </div>

              <div className="analysis-examples">
                <div className="panel-heading">
                  <div>
                    <span className="eyebrow">Static examples</span>
                    <h3>How the signals behave</h3>
                  </div>
                </div>
                <div className="analysis-example-tabs">
                  {methodology.examples.map((example) => (
                    <button
                      type="button"
                      key={example.id}
                      className={example.id === selectedExample?.id ? 'analysis-example-active' : ''}
                      onClick={() => setExampleId(example.id)}
                    >
                      {example.title}
                    </button>
                  ))}
                </div>
                {selectedExample ? <ExampleView example={selectedExample} /> : null}
              </div>
            </>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

function ActionableSummary({ analysis }: { analysis: JvmDeltaSnapshot | null }) {
  if (!analysis) {
    return (
      <div className="analysis-action-empty">
        <Icon icon="time" size={18} />
        <div>
          <strong>Building an evidence window</strong>
          <span>Collecting runtime samples before producing actionable analysis.</span>
        </div>
      </div>
    );
  }

  const leak = riskLevel(analysis.leakScore);
  const cpu = riskLevel(analysis.cpuAnalysis?.score ?? 0);
  const io = ioRiskLevel(analysis.ioAnalysis);

  return (
    <div className="analysis-actionable">
      <div className="analysis-action-header">
        <div>
          <span className="eyebrow">Operator view</span>
          <h3>What needs attention now</h3>
        </div>
        <span className="analysis-window-badge">
          {analysis.windowGcCollections} GC events · {Math.max(1, Math.round(analysis.intervalMillis / 1000))}s latest interval
        </span>
      </div>

      <div className="analysis-risk-grid">
        <ActionMetric
          label="Memory leakage evidence"
          value={analysis.leakScore}
          state={leak}
          message={leakMessage(analysis.leakScore, analysis)}
          action={leak === 'danger'
            ? 'Inspect heap retention, old generation, GC reclaim, and class histogram growth.'
            : 'Keep the rolling window running and watch whether post-GC retention continues to rise.'}
        />
        <ActionMetric
          label="CPU pressure"
          value={analysis.cpuAnalysis?.score ?? 0}
          state={cpu}
          message={cpuMessage(analysis.cpuAnalysis)}
          action={cpu === 'danger'
            ? 'Inspect top CPU-consuming threads and check CPU throttling or competing workloads.'
            : 'Compare pressure with application workload; high CPU can be expected during legitimate work.'}
        />
        <ActionMetric
          label="I/O activity"
          value={analysis.ioAnalysis?.score ?? 0}
          state={io}
          message={ioMessage(analysis.ioAnalysis)}
          action={io === 'danger'
            ? 'Check I/O latency, repeated file access, logging volume, and storage contention.'
            : 'High activity alone is not a fault; use I/O persistence to determine whether it is sustained.'}
        />
      </div>

      <div className="analysis-action-rule">
        <Icon icon="arrow-right" size={15} />
        <strong>Read activity together with persistence.</strong>
        <span>
          A burst can be normal. Sustained abnormal behavior is what turns an observation into an investigation signal.
        </span>
      </div>
    </div>
  );
}

function ActionMetric({
  label,
  value,
  state,
  message,
  action,
}: {
  label: string;
  value: number;
  state: 'good' | 'warning' | 'danger';
  message: string;
  action: string;
}) {
  return (
    <article className={`analysis-risk-card analysis-risk-${state}`}>
      <div className="analysis-risk-top">
        <span>{label}</span>
        <strong>{Math.round(value)}<small>/100</small></strong>
      </div>
      <b>{state === 'danger' ? 'HIGH RISK' : state === 'warning' ? 'INVESTIGATE' : 'NO ELEVATED RISK'}</b>
      <p>{message}</p>
      <div className="analysis-next-action">
        <Icon icon="arrow-right" size={13} />
        <span>{action}</span>
      </div>
    </article>
  );
}

function riskLevel(score: number): 'good' | 'warning' | 'danger' {
  if (score >= 80) return 'danger';
  if (score >= 60) return 'warning';
  return 'good';
}

function ioRiskLevel(result: AnalysisResult | null): 'good' | 'warning' | 'danger' {
  if (!result) return 'good';
  const activity = result.score;
  const persistence = result.metrics.persistencePercent ?? 0;

  if (activity >= 80 && persistence >= 75) return 'danger';
  if (activity >= 60 || persistence >= 60) return 'warning';
  return 'good';
}

function leakMessage(score: number, analysis: JvmDeltaSnapshot): string {
  if (score >= 80) {
    return 'Strong evidence of persistent memory retention. If the behavior continues, available heap can be progressively consumed and lead toward OOM.';
  }
  if (score >= 60) {
    return 'Multiple retention signals are agreeing across the observed window. This is a meaningful early warning for a developing memory problem.';
  }
  if (analysis.windowHeapGrowthBytes > 0) {
    return 'Some upward heap movement is present, but the current evidence is not strong enough to classify it as a high-confidence leak signal.';
  }
  return 'No strong evidence of persistent memory-retention behavior in the current window.';
}

function cpuMessage(result: AnalysisResult | null): string {
  if (!result) return 'CPU history is not available yet.';
  if (result.score >= 80) return 'CPU demand is sustained at a high level. This can become a performance risk when the workload is CPU-bound or throttled.';
  if (result.score >= 60) return 'CPU demand is elevated across the recent process history. Determine whether the workload is expected to be CPU-intensive.';
  return 'Recent CPU behavior does not show elevated sustained pressure.';
}

function ioMessage(result: AnalysisResult | null): string {
  if (!result) return 'I/O history is not available yet.';
  const persistence = result.metrics.persistencePercent ?? 0;
  if (result.score >= 80 && persistence >= 75) return 'High I/O activity is also persistent. This is a sustained workload signal worth investigating for latency, contention, or inefficient access patterns.';
  if (result.score >= 60 || persistence >= 60) return 'I/O activity is elevated or persistent. Check the workload context before treating it as a problem.';
  return 'Recent I/O behavior is not showing elevated sustained activity.';
}

function findLatestAnalysis(snapshots: AggregatorSnapshot[]): JvmDeltaSnapshot | null {
  const deltas = snapshots
    .flatMap((snapshot) => snapshot.jvmSnapshots.map((jvm) => jvm.delta).filter(Boolean) as JvmDeltaSnapshot[])
    .sort((left, right) => right.intervalMillis - left.intervalMillis);

  return deltas[0] ?? null;
}

function ExampleView({ example }: { example: MethodologyExample }) {
  const maxHeap = Math.max(...example.samples.map((sample) => sample.heapUsedMb));

  return (
    <div className="analysis-example">
      <p>{example.description}</p>
      <div className="analysis-example-table">
        <div className="analysis-example-row analysis-example-header">
          <span>Time</span><span>Heap used</span><span>GC reclaimed</span>
        </div>
        {example.samples.map((sample) => (
          <div className="analysis-example-row" key={sample.time}>
            <span>{sample.time}</span>
            <span>
              <strong>{sample.heapUsedMb} MB</strong>
              <span className="analysis-bar" style={{ width: `${Math.max(8, (sample.heapUsedMb / maxHeap) * 100)}%` }} />
            </span>
            <span>{sample.gcReclaimedMb} MB</span>
          </div>
        ))}
      </div>
    </div>
  );
}
