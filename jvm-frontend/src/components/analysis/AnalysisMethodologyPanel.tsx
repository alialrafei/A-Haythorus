import React, { useEffect, useState } from 'react';
import { Icon, Spinner } from '@blueprintjs/core';
import { sidecarApi } from '../../api/sidecarApi';
import type { AnalysisMethodology, MethodologyExample } from '../../models/analysisMethodology';

export function AnalysisMethodologyPanel() {
  const [methodology, setMethodology] = useState<AnalysisMethodology | null>(null);
  const [open, setOpen] = useState(false);
  const [exampleId, setExampleId] = useState('healthy-gc');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || methodology) return;

    let cancelled = false;
    setLoading(true);
    void sidecarApi.getAnalysisMethodology()
      .then((data) => {
        if (!cancelled) {
          setMethodology(data);
          setError(null);
        }
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof Error ? cause.message : 'Unable to load analysis methodology.');
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open, methodology]);

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
            <strong>How A-Haythorus calculates these numbers</strong>
          </span>
        </span>
        <Icon icon={open ? 'chevron-up' : 'chevron-down'} size={16} />
      </button>

      {open ? (
        <div className="analysis-methodology-body">
          {loading ? (
            <div className="inline-empty"><Spinner size={18} /> Loading methodology…</div>
          ) : error ? (
            <div className="inline-empty">{error}</div>
          ) : methodology ? (
            <>
              <div className="analysis-methodology-intro">
                <p>
                  These values are diagnostic evidence, not probabilities. A higher leak score means
                  the configured memory-retention signals agree more strongly over the observed window;
                  it does not prove that a leak exists or predict an OOM.
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
