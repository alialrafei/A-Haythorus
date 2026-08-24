# A-Haythorus JVM Leakage Scoring Model

This document defines the memory-retention and leak-confidence model used by A-Haythorus.

The core principle is:

> A single delta tells us movement. A leak is a behavior across time.

A-Haythorus therefore separates **pairwise delta measurement** from **historical leak analysis**.

---

## 1. Configuration model

The sampling resolution, retained history capacity, analysis window, and temporal smoothing are independent configuration knobs:

```properties
collector.interval.ms=5000
history.max.samples=120
leak.window.seconds=60
leak.ewma.alpha=0.35
```

Environment-variable equivalents are:

```text
AH_COLLECTOR_INTERVAL_MS
AH_HISTORY_MAX_SAMPLES
AH_LEAK_WINDOW_SECONDS
AH_LEAK_EWMA_ALPHA
```

Their roles are different:

```text
collector.interval.ms
    -> how often the JVM is sampled

history.max.samples
    -> hard memory bound for retained lightweight history

leak.window.seconds
    -> how far back the leak analyzer looks in time

leak.ewma.alpha
    -> how strongly the newest evidence influences confidence
```

The important relationship is:

```text
maximum retained duration ~= collector interval * history capacity
```

but leak analysis itself is **time based**, not sample-count based. If the collector changes from 5-second samples to 2-second samples, a 60-second leak window still means 60 seconds; it simply contains more observations.

---

## 2. Data model and memory bound

`JvmDataStore` keeps two views for each PID:

```text
latest full JvmSnapshot
        +
bounded lightweight history
```

The latest snapshot contains the rich monitoring data used by the API/UI.

History stores only lightweight `JvmHistorySample` records containing values required for trend analysis, such as:

```text
timestamp
heap used
non-heap used
old-generation used
thread count
GC collection counters
previous leak confidence
```

It deliberately does **not** retain a full thread dump or class histogram for every historical sample.

When the history exceeds `history.max.samples`, the oldest sample is removed:

```text
append newest sample
        |
        v
size > configured maximum?
        |
       yes
        v
remove oldest sample
```

This prevents the monitoring sidecar from developing its own unbounded memory growth.

---

## 3. Layer 1: pairwise delta

For each new snapshot `S_t` and immediately previous snapshot `S_(t-1)`:

```text
Delta_heap(t) = heap(t) - heap(t-1)
```

The signed value is retained because direction matters.

### Positive growth

```text
positiveHeapDelta(t) = max(Delta_heap(t), 0)
```

### Reclaimed movement

```text
reclaimedHeapBytes(t) = max(-Delta_heap(t), 0)
```

Therefore:

```text
+30 MB -> growth / possible retention evidence
-30 MB -> reclamation evidence
```

Pairwise delta answers:

> What happened during the latest sampling interval?

It does not answer:

> Is this JVM leaking over time?

---

## 4. Time-based historical analysis window

Let the current sample time be `t_now` and the configured analysis duration be `W` seconds.

A-Haythorus selects samples satisfying:

```text
timestamp >= t_now - W
```

For the default:

```text
W = 60 seconds
```

With 5-second sampling this is roughly 12 samples.

With 2-second sampling this is roughly 30 samples.

The semantic window remains the same: **the most recent 60 seconds of JVM behavior**.

---

## 5. Persistence

Persistence measures how consistently a metric moves in the suspicious direction across the analysis window.

Suppose the heap observations are:

```text
H_0, H_1, ..., H_n
```

For each interval:

```text
d_i = H_i - H_(i-1)
```

Count the positive intervals:

```text
N_positive = count(d_i > 0)
```

Then heap-growth persistence is:

```text
P_heap = N_positive / N_intervals
```

where:

```text
0 <= P_heap <= 1
```

### Example: sustained upward movement

```text
100 -> 104 -> 108 -> 111 -> 115
```

Movements:

```text
+4, +4, +3, +4
```

All four intervals are positive:

```text
P_heap = 4 / 4 = 1.0
```

This is highly persistent upward movement.

### Example: allocation/reclaim oscillation

```text
100 -> 110 -> 96 -> 108 -> 95
```

Movements:

```text
+10, -14, +12, -13
```

Only two of four intervals are positive:

```text
P_heap = 2 / 4 = 0.5
```

This is much less persistent and is more consistent with allocation followed by reclamation.

### Why persistence matters

Net growth by itself is not enough. Two JVMs can finish at similar heap sizes while having very different behavior.

A persistent pattern:

```text
100 -> 105 -> 110 -> 115 -> 120
```

is more suspicious than:

```text
100 -> 125 -> 95 -> 123 -> 120
```

because the first JVM repeatedly moves upward without meaningful recovery.

Persistence therefore answers:

> How consistently has the JVM moved in the suspicious direction?

It does **not** measure the magnitude of growth. Magnitude is handled separately.

---

## 6. Net window growth

Across the selected time window:

```text
G_heap = H_n - H_0
```

and percentage growth is:

```text
G_heap_pct = ((H_n - H_0) / H_0) * 100
```

This measures baseline movement over the entire recent time window rather than only the latest pair of samples.

A-Haythorus combines net growth with persistence because neither is sufficient alone.

---

## 7. Positive growth and reclaimed movement across the window

Across all pairwise movements:

```text
A_heap = sum(max(d_i, 0))
R_heap = sum(max(-d_i, 0))
```

where:

```text
A_heap = total observed positive heap movement
R_heap = total observed downward heap movement
```

These values describe what the sampled heap did. They are not direct proof that every negative movement came from GC.

---

## 8. GC-related reclaim evidence

GC collection counters are compared across the same time window.

Let:

```text
C_gc = total increase in GC collection counters
```

If no GC collection occurred, A-Haythorus does not claim that GC failed to reclaim memory.

When GC has run and positive heap movement exists, the current heuristic computes:

```text
reclaimRatio = min(1, R_heap / A_heap)
retentionRatio = 1 - reclaimRatio
```

and combines it with persistence:

```text
GC_score = 20 * retentionRatio * P_heap
```

This is an A-Haythorus heuristic, not a standardized JVM formula. It should be interpreted as sampled retention evidence.

A future stronger model should correlate heap floors specifically with intervals in which GC counters increased and eventually track post-major-GC baselines directly.

---

## 9. Old-generation trend

Heap usage contains expected young-generation allocation noise. Long-lived objects accumulating in old generation are stronger retention evidence.

A-Haythorus applies the same trend concepts to old-generation usage:

```text
OldGen_0, OldGen_1, ..., OldGen_n
```

and measures net old-generation growth, old-generation growth percentage, and positive-growth persistence.

This feature contributes up to 25 points to current leak evidence.

---

## 10. Histogram evidence

Histogram growth is supporting evidence.

If a class has:

```text
bytesDelta > 0
```

its latest relative growth is approximately:

```text
classGrowthPct = bytesDelta / previousBytes * 100
```

Histogram evidence contributes up to 15 points and is intentionally not sufficient on its own to classify a leak.

---

## 11. Thread accumulation evidence

Across the same time window:

```text
threadGrowth = threadCount_last - threadCount_first
```

Positive thread growth contributes up to 10 points. This is a smaller weight because thread accumulation is not equivalent to heap retention.

---

## 12. Current historical evidence score

The current time window produces independent evidence components:

```text
heap trend and persistence        30
old-generation trend              25
GC reclaim failure                20
histogram/object growth           15
thread accumulation               10
                                  ---
                                  100
```

Therefore:

```text
E_t = H_t + O_t + G_t + C_t + T_t
```

with:

```text
0 <= E_t <= 100
```

This value is exposed as `instantaneousLeakScore`: current-window evidence before historical smoothing.

---

## 13. Time-based window maturity

A newly started JVM should not reach high confidence before enough elapsed time has been observed.

Maturity is therefore based on elapsed window coverage:

```text
observedDuration = timestamp_last - timestamp_first
maturity = min(1, observedDuration / configuredWindowDuration)
```

For a 60-second configured window:

```text
15 seconds observed -> 0.25 maturity
30 seconds observed -> 0.50 maturity
45 seconds observed -> 0.75 maturity
60+ seconds         -> 1.00 maturity
```

Then:

```text
E_t_matured = E_t * maturity
```

This remains correct when the sampling interval changes.

---

## 14. Historical confidence with exponentially weighted memory

A persistent leak should gain confidence over time, while transient evidence should decay.

The weighted model can be written as:

```text
L_t = c_0 E_t + c_1 E_(t-1) + c_2 E_(t-2) + ...
```

A-Haythorus implements this recursively with EWMA:

```text
L_t = alpha * E_t + (1 - alpha) * L_(t-1)
```

Default:

```text
alpha = 0.35
historical weight = 0.65
```

so:

```text
L_t = 0.35 E_t + 0.65 L_(t-1)
```

This value is exposed as `leakScore` and should be interpreted as leak confidence, not proof of a leak.

---

## 15. Severity mapping

```text
0  - 29   LOW
30 - 59   MEDIUM
60 - 79   HIGH
80 - 100  CRITICAL
```

Severity is applied to historical leak confidence rather than one local interval.

---

## 16. Architectural flow

```text
Target JVM
   |
   v
JvmCollector
   |
   +--> latest full snapshot
   |        |
   |        +--> pairwise delta
   |
   +--> bounded lightweight history
             |
             +--> select samples by leak.window.seconds
                         |
                         v
                    persistence
                    net growth
                    reclamation
                    old-gen trend
                    GC counters
                         |
                         v
                 current evidence E_t
                         |
                         v
          L_t = alpha E_t + (1-alpha)L_(t-1)
                         |
                         v
              leak confidence / severity
```

---

## 17. Interpretation and limitations

A-Haythorus reports **leak confidence**, not proof of a leak.

The current model is intentionally deterministic and explainable. Important future improvements include post-major-GC baseline detection, GC-event-aligned reclamation, regression slope over heap and old-generation history, noise tolerance, collector-specific GC semantics, slower independent histogram collection, class-level historical retention, process-start identity to guard against PID reuse, and benchmark workloads for known healthy and deliberately leaky JVMs.

The semantic separation remains:

```text
pairwise delta
    = what changed now?

persistence/trend
    = what behavior is repeating across the recent time window?

leak confidence
    = how strongly does accumulated historical evidence support retention?
```
