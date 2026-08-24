# A-Haythorus JVM Leakage Scoring Model

This document defines the memory-retention and leakage-confidence model used by A-Haythorus.

The central idea is simple:

> A single delta tells us movement. A leak is a behavior across time.

A-Haythorus therefore separates **pairwise delta measurement** from **historical leak analysis**.

---

## 1. Why the old two-snapshot model was insufficient

The original model compared only two snapshots:

```text
S(t-1) ---- 5 seconds ----> S(t)
```

For heap usage this produced:

```text
heapDelta = heap(t) - heap(t-1)
```

That value is useful, but it cannot answer whether memory is leaking.

For example, this JVM:

```text
40 MB -> 43 MB -> 46 MB -> 49 MB -> 52 MB -> 55 MB
```

has only small local changes, but the persistent upward movement is suspicious.

A healthy JVM can show large pairwise growth while still reclaiming memory:

```text
100 MB -> 160 MB -> 92 MB -> 155 MB -> 95 MB
```

The positive allocation bursts are not, by themselves, evidence of a leak because the JVM repeatedly returns to a similar baseline.

Therefore:

```text
pairwise delta != leak diagnosis
```

---

## 2. Data model

`JvmDataStore` keeps two views of each PID:

```text
latest snapshot
    +
bounded history
```

The history is a per-PID ring-like bounded deque.

Current configuration:

```text
sample interval      = 5 seconds
maximum history      = 120 snapshots
retained duration    ~= 10 minutes
```

Leak analysis does not need to scan all ten minutes on every sample. It currently analyzes the most recent 12 samples:

```text
analysis window      = 12 snapshots
window duration      ~= 60 seconds
```

The longer datastore history remains available for future UI/backend history endpoints and longer-term analysis.

---

## 3. Layer 1: pairwise delta

For every new snapshot `S_t`, the immediately previous snapshot is `S_(t-1)`.

### Signed heap movement

```text
Delta_heap(t) = heap(t) - heap(t-1)
```

The signed delta is retained because direction matters.

### Positive growth

```text
positiveHeapDelta(t) = max(Delta_heap(t), 0)
```

This tells us how many bytes were added during the interval.

### Reclaimed bytes

```text
reclaimedHeapBytes(t) = max(-Delta_heap(t), 0)
```

This tells us how many bytes disappeared during the interval.

The same separation is applied to non-heap memory.

This is important because:

```text
+30 MB  -> growth / possible retention evidence
-30 MB  -> reclamation evidence
```

They should not be treated as equivalent magnitudes.

Pairwise delta answers:

> What happened during the latest sampling interval?

It does not answer:

> Is the JVM leaking?

---

## 4. Layer 2: historical trend features

Let the current analysis window contain heap observations:

```text
H_0, H_1, ..., H_n
```

where `H_i` is heap-used bytes for snapshot `i`.

For every interval:

```text
d_i = H_i - H_(i-1)
```

### 4.1 Positive-growth persistence

Count the number of intervals with positive heap movement:

```text
N_positive = count(d_i > 0)
```

Then:

```text
P_heap = N_positive / N_intervals
```

`P_heap` is in `[0, 1]`.

Examples:

```text
100 -> 103 -> 106 -> 109 -> 112

movements: + + + +
P_heap = 4 / 4 = 1.0
```

versus:

```text
100 -> 115 -> 96 -> 112 -> 98

movements: + - + -
P_heap = 2 / 4 = 0.5
```

The first pattern is much more consistent with sustained retention.

### 4.2 Net window growth

```text
G_heap = H_n - H_0
```

and percentage growth:

```text
G_heap_pct = ((H_n - H_0) / H_0) * 100
```

This measures the actual movement of the baseline across the entire window instead of only the latest five seconds.

### 4.3 Positive growth and reclamation totals

Across the window:

```text
A_heap = sum(max(d_i, 0))
R_heap = sum(max(-d_i, 0))
```

where:

```text
A_heap = observed positive allocation/growth movement
R_heap = observed reclaimed movement
```

These totals allow GC effectiveness to be represented explicitly.

---

## 5. Garbage-collection reclaim evidence

GC activity is measured across the same historical window.

Let:

```text
C_gc = total increase in GC collection counters across collectors
```

If there are no GC collections, we do not claim GC failed to reclaim memory.

When GC has run and positive heap growth exists, calculate:

```text
reclaimRatio = min(1, R_heap / A_heap)
```

Then:

```text
retentionRatio = 1 - reclaimRatio
```

Interpretation:

```text
reclaimRatio ~= 1.0
```

means most observed growth was later reclaimed.

```text
reclaimRatio ~= 0.0
```

means observed positive growth was largely retained.

The GC evidence score also includes heap-growth persistence:

```text
GC_score = 20 * retentionRatio * P_heap
```

This prevents a single allocation burst from being treated like persistent retention.

### Healthy pattern

```text
100 -> 150 -> 92 -> 145 -> 95
```

The JVM allocates heavily but repeatedly reclaims memory.

### Suspicious pattern

```text
100 -> 150 -> 125 -> 180 -> 155 -> 210
```

GC runs, but the floor keeps rising.

This rising post-reclamation baseline is much stronger leak evidence than raw heap growth alone.

---

## 6. Old-generation trend

Heap growth alone can be noisy because young-generation allocations are expected.

Objects surviving GC and accumulating in old generation are stronger retention evidence.

A-Haythorus applies the same trend model to the detected old-generation memory pool:

```text
OldGen_0, OldGen_1, ..., OldGen_n
```

and calculates:

- old-generation net growth
- old-generation growth percentage
- old-generation positive-growth persistence

The current detector recognizes pool names containing values such as:

```text
Old Gen
Tenured
old generation
```

The old-generation feature contributes up to 25 points to current leak evidence.

---

## 7. Histogram evidence

Class histogram growth is supporting evidence.

If the latest histogram delta contains a class whose retained bytes are increasing:

```text
bytesDelta > 0
```

then its relative growth is:

```text
classGrowthPct = bytesDelta / previousBytes * 100
```

This contributes up to 15 points.

For a deliberate leak such as:

```java
static final List<byte[]> HEAP = new ArrayList<>();
HEAP.add(new byte[1024 * 1024]);
```

we expect byte-array (`[B`) retained bytes to trend upward.

Histogram evidence is intentionally not sufficient on its own to classify a leak. It corroborates the broader trend.

---

## 8. Thread accumulation evidence

A JVM can also leak resources through uncontrolled thread creation.

Across the analysis window:

```text
threadGrowth = threadCount_last - threadCount_first
```

Positive persistent thread growth contributes up to 10 points.

This is deliberately a smaller weight than heap/old-generation/GC evidence because thread growth is not equivalent to heap retention.

---

## 9. Instantaneous historical evidence score

The current window produces multiple independent features.

Maximum contributions are:

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

This is called:

```text
instantaneousLeakScore
```

Despite the name, it is not based on only one pair of points. It is the evidence extracted from the current historical window before temporal confidence smoothing.

---

## 10. Window maturity

Two samples are not enough to establish a stable trend.

A-Haythorus therefore scales evidence while the analysis window is young.

Current maturity function:

```text
maturity = min(1, intervals / 5)
```

Then:

```text
E_t_matured = E_t * maturity
```

Examples:

```text
2 snapshots -> 1 interval  -> 20% maturity
3 snapshots -> 2 intervals -> 40% maturity
4 snapshots -> 3 intervals -> 60% maturity
5 snapshots -> 4 intervals -> 80% maturity
6+ snapshots              -> 100% maturity
```

This prevents the system from declaring high-confidence leakage immediately after startup.

---

## 11. Historical confidence with exponentially weighted memory

A persistent leak should gain confidence over time. A temporary burst should decay.

The user's proposed model can be written as:

```text
L_t = c_0 E_t + c_1 E_(t-1) + c_2 E_(t-2) + ...
```

where recent evidence receives larger weight.

A-Haythorus implements the equivalent recursive exponentially weighted moving average (EWMA):

```text
L_t = alpha * E_t + (1 - alpha) * L_(t-1)
```

Current value:

```text
alpha = 0.35
historical weight = 0.65
```

So:

```text
L_t = 0.35 * E_t + 0.65 * L_(t-1)
```

Expanding recursively gives:

```text
L_t
 = 0.35 E_t
 + 0.35(0.65) E_(t-1)
 + 0.35(0.65^2) E_(t-2)
 + ...
```

Therefore the effective historical coefficients are:

```text
current evidence       0.3500
1 sample ago           0.2275
2 samples ago          0.1479
3 samples ago          0.0961
4 samples ago          0.0625
...
```

Older evidence never disappears abruptly; its influence decays exponentially.

This value is exposed as:

```text
leakScore
```

and should be interpreted as **leak confidence**, not proof of a leak.

---

## 12. Why EWMA is preferable to a plain average

A plain average gives every point equal weight:

```text
mean = (E_1 + E_2 + ... + E_n) / n
```

That creates two problems:

1. Ancient behavior can dominate current state for too long.
2. A JVM that recovered from a transient event can remain marked suspicious because old high scores remain equally important.

EWMA gives the system memory while still allowing recovery:

```text
persistent evidence -> confidence rises
vanishing evidence   -> confidence decays
```

This matches the semantics of runtime monitoring better than an unweighted mean.

---

## 13. Severity mapping

The historical confidence is mapped to severity:

```text
0  - 29   LOW
30 - 59   MEDIUM
60 - 79   HIGH
80 - 100  CRITICAL
```

The threshold is applied to the smoothed confidence `L_t`, not directly to one five-second delta.

---

## 14. Example: steady leak

Suppose heap history is:

```text
40
43
46
49
52
55 MB
```

Then:

```text
positive persistence = 5 / 5 = 1.0
net growth            = +15 MB
reclamation           ~= 0
```

If GC also ran during this window and the old generation increased, the current evidence becomes high.

The historical confidence then increases gradually:

```text
sample   evidence   previous confidence   new confidence
1          10               0                  10
2          25              10                  15
3          45              15                  26
4          65              26                  40
5          80              40                  54
6          90              54                  67
```

The exact numbers depend on observed signals, but the intended behavior is:

```text
persistent evidence => stronger confidence over time
```

---

## 15. Example: temporary allocation burst

Consider:

```text
100 -> 180 -> 105 -> 175 -> 102
```

Heap repeatedly grows, but GC/reclamation returns the process close to its original floor.

We observe:

```text
positive growth: high
reclaimed bytes: also high
net window growth: small
reclaim ratio: high
persistence: mixed
```

Therefore the leak evidence remains low or decays after the burst.

This is the behavior the old two-snapshot detector could not distinguish reliably.

---

## 16. Delta semantics after this change

The delta object now has two responsibilities, clearly separated by field meaning.

### Latest movement fields

```text
heapDelta
positiveHeapDelta
reclaimedHeapBytes
heapGrowthPercentage

nonHeapDelta
positiveNonHeapDelta
reclaimedNonHeapBytes
nonHeapGrowthPercentage

threadDelta
gcDelta
poolDelta
histogramDelta
```

These answer:

> What changed since the previous snapshot?

### Historical analysis fields

```text
instantaneousLeakScore
leakScore
leakSeverity
heapGrowthPersistence
windowHeapGrowthBytes
windowGcCollections
historicalWeight
leakReasons
recommendations
```

These answer:

> What does the recent behavior imply about persistent retention?

---

## 17. Architectural flow

```text
Target JVM
   |
   | JMX / diagnostic commands
   v
JvmCollector
   |
   v
new JvmSnapshot
   |
   +------------------------------+
   |                              |
   v                              v
pairwise DeltaEngine         JvmDataStore history
(previous vs current)        last N snapshots
   |                              |
   +--------------+---------------+
                  |
                  v
        HistoricalLeakAnalyzer
                  |
          +-------+--------+
          |                |
          v                v
 current evidence      previous confidence
        E_t                 L_(t-1)
          |                |
          +-------+--------+
                  |
                  v
      L_t = 0.35 E_t + 0.65 L_(t-1)
                  |
                  v
          leakScore / severity
                  |
                  v
             JvmSnapshot
                  |
                  v
             JvmDataStore
                  |
                  v
               REST/UI
```

---

## 18. Important limitations and next improvements

This model is intentionally explainable and deterministic. It is not intended to be the final statistical detector.

Future improvements should consider:

- detecting post-major-GC baselines directly rather than inferring reclaim from sample-to-sample movement
- linear-regression slope over heap and old-generation history
- confidence intervals/noise tolerance
- collector-specific GC semantics (G1, ZGC, Shenandoah, Parallel GC)
- longer and configurable analysis windows
- slower histogram sampling so expensive diagnostic commands do not block every fast sample
- class-level historical retention instead of only latest histogram delta
- process-start identity to prevent PID reuse from joining unrelated histories
- dedicated lightweight history DTOs for REST/UI instead of retaining full snapshots indefinitely
- tests that compare known healthy allocation workloads against deterministic leak workloads

---

## 19. Interpretation rule

A-Haythorus must present the result as:

```text
Leak confidence
```

rather than claiming:

```text
A leak is proven
```

Runtime telemetry provides evidence. Heap-dump/reference analysis is still required to establish the exact retained-object cause.
