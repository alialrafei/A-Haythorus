import React, { useEffect, useState } from 'react';
import { Icon, Spinner } from '@blueprintjs/core';
import { sidecarApi } from '../../api/sidecarApi';
import { useMonitoring } from '../../context/MonitoringContext';
import type {
  AggregatorSnapshot,
  AnalysisResult,
  JvmDeltaSnapshot,
  JvmHistoryResponse,
} from '../../models/snapshot';
import type { AnalysisMethodology, MethodologyExample } from '../../models/analysisMethodology';

type AnalysisContext = {
  analysis: JvmDeltaSnapshot | null;
  sampleCount: number;
  windowSeconds: number;
};

export function AnalysisMethodologyPanel() {
  const { sharding, selectedShard } = useMonitoring();
  const [methodology, setMethodology] = useState<AnalysisMethodology | null>(null);
  const [context, setContext] = useState<AnalysisContext | null>(null);
  const [open, setOpen] = useState(true);
  const [exampleId, setExampleId] = useState('healthy-gc');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      try {
        const shard = sharding.enabled ? selectedShard : null;
        const [methodologyData, snapshots, histories] = await Promise.all([
          sidecarApi.getAnalysisMethodology(),
          sidecarApi.getSnapshots(shard),
          sidecarApi.getHistories(shard),
        ]);

        if (!cancelled) {
          setMethodology(methodologyData);
          setContext(buildAnalysisContext(snapshots, histories));
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

    if (!open) return undefined;

    void load();
    const timer = window.setInterval(() => void load(), 5_000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [open, selectedShard, sharding.enabled]);

  const selectedExample = methodology?.examples.find((example) => example.id === exampleId)
    ?? methodology?.examples[0];

  return (
    <section className="panel analysis-methodology">
      <button type="button" className="analysis-methodology-toggle" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
        <span className="analysis-methodology-title"><Icon icon="search-template" size={16} /><span><span className="eyebrow">Analysis evidence</span><strong>See how the score was earned</strong></span></span>
        <Icon icon={open ? 'chevron-up' : 'chevron-down'} size={16} />
      </button>
      {open ? (
        <div className="analysis-methodology-body">
          {loading && !methodology ? <div className="inline-empty"><Spinner size={18} /> Building analysis evidence…</div>
            : error && !methodology ? <div className="inline-empty">{error}</div>
            : methodology ? <><EvidenceSummary context={context} /><div className="analysis-methodology-intro"><strong>The score is evidence, not a magic number</strong><p>A-Haythorus samples runtime behavior over a rolling analysis window, measures how the behavior changes between samples, and combines independent signals into an explainable score. The score is a 0–100 strength-of-evidence scale; it is not a probability.</p></div><div className="analysis-methodology-grid">{methodology.sections.map((section) => <article key={section.id} className="analysis-methodology-card"><h4>{section.title}</h4><p>{section.description}</p>{section.formula ? <code className="analysis-formula">{section.formula}</code> : null}{section.signals?.length ? <ul>{section.signals.map((signal) => <li key={signal.id}><strong>{signal.name}:</strong> {signal.description}</li>)}</ul> : null}{section.scoreFlow?.length ? <ul>{section.scoreFlow.map((item) => <li key={item}>{item}</li>)}</ul> : null}</article>)}</div><div className="analysis-examples"><div className="panel-heading"><div><span className="eyebrow">Model behavior</span><h3>What different sampling patterns mean</h3></div></div><div className="analysis-example-tabs">{methodology.examples.map((example) => <button type="button" key={example.id} className={example.id === selectedExample?.id ? 'analysis-example-active' : ''} onClick={() => setExampleId(example.id)}>{example.title}</button>)}</div>{selectedExample ? <ExampleView example={selectedExample} /> : null}</div></>
            : null}
        </div>
      ) : null}
    </section>
  );
}

function EvidenceSummary({ context }: { context: AnalysisContext | null }) {
  if (!context?.analysis) return <div className="analysis-action-empty"><Icon icon="time" size={18} /><div><strong>Building the evidence window</strong><span>More runtime samples are required before the analysis can explain a score.</span></div></div>;
  const analysis = context.analysis;
  const leakState = riskLevel(analysis.leakScore);
  const cpuState = riskLevel(analysis.cpuAnalysis?.score ?? 0);
  const ioState = ioRiskLevel(analysis.ioAnalysis);
  return <div className="analysis-actionable"><div className="analysis-action-header"><div><span className="eyebrow">Current evidence</span><h3>What the analyzer sees</h3></div><span className="analysis-window-badge">{context.sampleCount} samples · {context.windowSeconds.toFixed(1)}s window · {analysis.windowGcCollections} GC events</span></div><div className="analysis-risk-grid"><EvidenceCard label="Memory retention" score={analysis.leakScore} state={leakState} statement={leakStatement(analysis)} evidence={analysis.leakReasons?.slice(0, 4) ?? []} action="Inspect Memory and Objects when this signal is elevated." /><EvidenceCard label="CPU pressure" score={analysis.cpuAnalysis?.score ?? 0} state={cpuState} statement={cpuStatement(analysis.cpuAnalysis)} evidence={analysis.cpuAnalysis?.evidence.filter((item) => item.available).slice(0, 3).map((item) => `${item.name}: ${formatEvidence(item.value)}`) ?? []} action="Inspect Threads and top CPU consumers when pressure persists." /><EvidenceCard label="I/O behavior" score={analysis.ioAnalysis?.score ?? 0} state={ioState} statement={ioStatement(analysis.ioAnalysis)} evidence={analysis.ioAnalysis?.evidence.filter((item) => item.available).slice(0, 3).map((item) => `${item.name}: ${formatEvidence(item.value)}`) ?? []} action="Inspect I/O persistence and process throughput before investigating storage." /></div><div className="analysis-action-rule"><Icon icon="calculator" size={15} /><strong>Why this is trustworthy:</strong><span>the result is produced from sampled history, explicit signal formulas, and the evidence shown below—not from a single instantaneous metric.</span></div></div>;
}

function EvidenceCard({ label, score, state, statement, evidence, action }: { label: string; score: number; state: 'good' | 'warning' | 'danger'; statement: string; evidence: string[]; action: string }) {
  const stateLabel = state === 'danger' ? 'HIGH RISK' : state === 'warning' ? 'INVESTIGATE' : 'NORMAL';
  return <article className={`analysis-risk-card analysis-risk-${state}`}><div className="analysis-risk-top"><span>{label}</span><strong>{Math.round(score)}<small>/100</small></strong></div><b>{stateLabel}</b><p>{statement}</p>{evidence.length > 0 ? <ul className="analysis-evidence-points">{evidence.map((item) => <li key={item}>{item}</li>)}</ul> : null}<div className="analysis-next-action"><Icon icon="arrow-right" size={13} /><span>{action}</span></div></article>;
}

function riskLevel(score: number): 'good' | 'warning' | 'danger' { if (score >= 80) return 'danger'; if (score >= 60) return 'warning'; return 'good'; }
function ioRiskLevel(result: AnalysisResult | null): 'good' | 'warning' | 'danger' { if (!result) return 'good'; const persistence = result.metrics.persistencePercent ?? 0; if (result.score >= 80 && persistence >= 75) return 'danger'; if (result.score >= 60 || persistence >= 60) return 'warning'; return 'good'; }
function leakStatement(analysis: JvmDeltaSnapshot): string { if (analysis.leakScore >= 80) return 'Strong evidence of persistent memory retention across the observed window. Continued retention can progressively consume available heap.'; if (analysis.leakScore >= 60) return 'Multiple memory-retention signals are agreeing across the observed window. This is an actionable early warning.'; if (analysis.windowHeapGrowthBytes > 0) return 'Heap has moved upward, but the current evidence is still developing.'; return 'No elevated persistent memory-retention signal in the current analysis window.'; }
function cpuStatement(result: AnalysisResult | null): string { if (!result) return 'CPU history is not available yet.'; if (result.score >= 80) return 'CPU demand is sustained at a high level and may be limiting application throughput.'; if (result.score >= 60) return 'CPU demand is elevated across recent samples; determine whether this matches expected workload behavior.'; return 'Recent samples do not indicate elevated sustained CPU pressure.'; }
function ioStatement(result: AnalysisResult | null): string { if (!result) return 'I/O history is not available yet.'; const persistence = result.metrics.persistencePercent ?? 0; if (result.score >= 80 && persistence >= 75) return 'High I/O activity is persistent across the observed window, making it worth investigating for latency, contention, or inefficient access.'; if (result.score >= 60 || persistence >= 60) return 'I/O activity is elevated or persistent; use the supporting throughput and persistence signals to investigate.'; return 'Recent samples do not indicate elevated sustained I/O behavior.'; }
function formatEvidence(value: number): string { return `${Math.round(value * 100)}%`; }

function buildAnalysisContext(snapshots: AggregatorSnapshot[], histories: JvmHistoryResponse[]): AnalysisContext {
  const candidates = snapshots.flatMap((snapshot) => snapshot.jvmSnapshots.filter((jvm) => jvm.delta).map((jvm) => ({ timestamp: toMillis(jvm.timestamp), delta: jvm.delta as JvmDeltaSnapshot, pid: jvm.pid, namespace: snapshot.pod.namespace, podName: snapshot.pod.name })));
  candidates.sort((left, right) => right.timestamp - left.timestamp);
  const latest = candidates[0] ?? null;
  if (!latest) return { analysis: null, sampleCount: 0, windowSeconds: 0 };
  const matchingHistory = histories.find((history) => history.pid === latest.pid && history.pod.namespace === latest.namespace && history.pod.name === latest.podName);
  const samples = matchingHistory?.history?.length ?? 0;
  const windowSeconds = matchingHistory?.history && matchingHistory.history.length > 1 ? Math.max(0, (toMillis(matchingHistory.history.at(-1)!.timestamp) - toMillis(matchingHistory.history[0].timestamp)) / 1000) : 0;
  return { analysis: latest.delta, sampleCount: samples, windowSeconds };
}
function toMillis(value: string | number): number { if (typeof value === 'number') return value > 10_000_000_000 ? value : value * 1000; const parsed = Date.parse(value); return Number.isFinite(parsed) ? parsed : 0; }
function ExampleView({ example }: { example: MethodologyExample }) { const maxHeap = Math.max(...example.samples.map((sample) => sample.heapUsedMb)); return <div className="analysis-example"><p>{example.description}</p><div className="analysis-example-table"><div className="analysis-example-row analysis-example-header"><span>Time</span><span>Heap used</span><span>GC reclaimed</span></div>{example.samples.map((sample) => <div className="analysis-example-row" key={sample.time}><span>{sample.time}</span><span><strong>{sample.heapUsedMb} MB</strong><span className="analysis-bar" style={{ width: `${Math.max(8, (sample.heapUsedMb / maxHeap) * 100)}%` }} /></span><span>{sample.gcReclaimedMb} MB</span></div>)}</div></div>; }
