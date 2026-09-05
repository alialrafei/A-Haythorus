# A-Haythorus JVM Memory-Retention Scoring Model

This document defines the current JVM memory-retention heuristics used by A-Haythorus.

The most important rule is:

> A delta tells us movement. A leak is a behavior across time.

The model therefore separates pairwise measurement from historical evidence aggregation.

This document distinguishes three kinds of formulas:

1. **Measured values** from JVM/OS counters.
2. **Mathematical normalizations** used to map measurements into `[0,1]` evidence.
3. **A-Haythorus heuristics** used to combine those normalized signals.

The resulting score is diagnostic evidence strength, not proof or probability of a leak.

---

## 1. Configuration

```properties
collector.interval.ms=10000
history.max.samples=120
leak.window.seconds=60
leak.ewma.alpha=0.35

analysis.memory.heap-retention.weight=1.0
analysis.memory.old-gen-retention.weight=1.0
analysis.memory.gc-reclaim.weight=1.0
analysis.memory.histogram-growth.weight=1.0
```

Environment equivalents:

```text
AH_COLLECTOR_INTERVAL_MS
AH_HISTORY_MAX_SAMPLES
AH_LEAK_WINDOW_SECONDS
AH_LEAK_EWMA_ALPHA

AH_ANALYSIS_MEMORY_HEAP_RETENTION_WEIGHT
AH_ANALYSIS_MEMORY_OLD_GEN_RETENTION_WEIGHT
AH_ANALYSIS_MEMORY_GC_RECLAIM_WEIGHT
AH_ANALYSIS_MEMORY_HISTOGRAM_GROWTH_WEIGHT
```

The default collection interval is 10 seconds.

The analysis window is time-based. With a 60-second window and a 10-second cadence, the analyzer typically sees roughly six intervals once the window is mature.

---

## 2. Glossary of symbols

| Symbol | Meaning | Unit |
|---|---|---|
| `t_i` | timestamp of sample `i` | time |
| `H_i` | heap used at sample `i` | bytes |
| `O_i` | old-generation used at sample `i` | bytes |
| `d_i` | movement between adjacent samples | bytes |
| `N` | number of valid intervals | count |
| `N+` | number of positive-growth intervals | count |
| `G+` | total positive growth | bytes |
| `R` | total reclaimed movement | bytes |
| `G_net` | last value minus first value | bytes |
| `P` | persistence | `[0,1]` |
| `T` | retention ratio | `[0,1]` |
| `E_i` | normalized evidence signal | `[0,1]` |
| `w_i` | relative signal weight | non-negative |
| `E` | weighted combined evidence | `[0,1]` |
| `M` | window maturity | `[0,1]` |
| `L_t` | smoothed historical leak confidence | `[0,100]` |
| `alpha` | newest-evidence smoothing weight | `(0,1]` |

---

## 3. Pairwise movement

For adjacent heap samples:

```text
d_i = H_i - H_(i-1)
```

Positive movement:

```text
positive_i = max(d_i, 0)
```

Reclaimed movement:

```text
reclaimed_i = max(-d_i, 0)
```

Across a historical window:

```text
G+ = sum(positive_i)
R  = sum(reclaimed_i)
G_net = H_last - H_first
```

These are measurements, not leak scores.

---

## 4. Persistence

Persistence measures how consistently a metric moves upward.

```text
P = N+ / N
```

where:

```text
N+ = count(d_i > 0)
N  = total valid intervals
```

Example:

```text
100 -> 104 -> 108 -> 111 -> 115
```

Movements:

```text
+4, +4, +3, +4
```

So:

```text
P = 4 / 4 = 1.0
```

Another example:

```text
100 -> 110 -> 96 -> 108 -> 95
```

Movements:

```text
+10, -14, +12, -13
```

So:

```text
P = 2 / 4 = 0.5
```

Persistence measures consistency, not magnitude.

---

## 5. Retention ratio

The analyzer compares net retained growth with the amount of positive growth observed:

```text
T = max(G_net, 0) / G+
```

when `G+ > 0`.

Interpretation:

```text
T near 0 -> most upward movement did not remain retained
T near 1 -> most upward movement remained in the ending level
```

If net growth is non-positive, retention evidence is zero.

---

## 6. Heap evidence

A-Haythorus combines persistence and retention with a simple mean:

```text
E_heap = (P_heap + T_heap) / 2
```

This is an A-Haythorus heuristic.

It intentionally rewards a pattern that is both:

- repeatedly upward, and
- still elevated at the end of the window.

---

## 7. Old-generation evidence

The same directional model is applied to resolved old-generation history:

```text
E_old = (P_old + T_old) / 2
```

Old-generation evidence is unavailable if no suitable old-generation pool can be resolved.

Unavailable is different from zero.

---

## 8. GC reclaim evidence

A-Haythorus only evaluates this signal when at least one GC collection occurred in the analysis window.

First:

```text
reclaimRatio = R / G+
```

clamped to `[0,1]`.

Then:

```text
retentionRatio = 1 - reclaimRatio
```

And the heuristic evidence becomes:

```text
E_gc = retentionRatio * P_heap
```

Interpretation:

```text
high reclaim + low persistence -> weak suspicious evidence
low reclaim + high persistence -> stronger suspicious evidence
```

If no GC occurs, the signal is unavailable because the analyzer did not observe a collection opportunity to judge reclamation.

This formula is a heuristic; it is not a collector-specific proof of live-object retention after a major GC.

---

## 9. Histogram evidence

Histogram movement is aggregated over all matched classes before top-N UI truncation.

For each matched class delta:

```text
classDelta = currentBytes - previousBytes
```

Aggregate:

```text
positiveBytes  = sum(max(classDelta, 0))
reclaimedBytes = sum(max(-classDelta, 0))
```

Growth dominance:

```text
growthDominance = positiveBytes / (positiveBytes + reclaimedBytes)
```

Top-class concentration:

```text
topClassShare = largestPositiveClassDelta / positiveBytes
```

Histogram evidence:

```text
E_hist = (growthDominance + topClassShare) / 2
```

This is an A-Haythorus heuristic.

It answers two questions:

1. Was matched-class movement mostly upward?
2. Was a large share of that upward movement concentrated in one dominant class?

It does not yet measure the same class persisting across many historical windows.

---

## 10. Available versus unavailable evidence

Every evidence signal has two independent concepts:

```text
value     in [0,1]
available true/false
```

Examples:

```text
E = 0, available=true
```

means the signal was observed and showed no suspicious evidence.

```text
available=false
```

means the signal could not be evaluated.

Unavailable signals must not lower the aggregate score.

---

## 11. Weighted evidence aggregation

For available signals with positive weights:

```text
             sum(w_i * E_i)
E_total = ---------------------
              sum(w_i)
```

Default weights are all `1.0`, so the default is the arithmetic mean of available evidence.

Important properties:

```text
weight = 0    -> disables that signal
weight < 0    -> invalid configuration
all weights * c -> same final score for c > 0
```

Weights express relative policy importance, not raw measurement scale.

The formula that converts raw measurements into `E_i` is separate from the weight used to combine `E_i` with other evidence.

---

## 12. Window maturity

A partially observed window should contribute less than a fully observed one.

Let:

```text
observedSeconds = lastTimestamp - firstTimestamp
W               = configured leak window seconds
```

Then:

```text
M = min(1, observedSeconds / W)
```

Invalid or non-increasing timestamps produce maturity `0`.

Current-window score:

```text
instantaneousLeakScore = E_total * 100 * M
```

This replaces sample-count-based maturity assumptions.

---

## 13. Historical smoothing

The primary leak confidence is smoothed over time:

```text
L_t = alpha * E_t + (1 - alpha) * L_(t-1)
```

where `E_t` is the current-window score after maturity scaling.

Default:

```text
alpha = 0.35
```

so:

```text
L_t = 0.35 * E_t + 0.65 * L_(t-1)
```

Recursive expansion gives:

```text
L_t = alpha E_t
    + alpha(1-alpha) E_(t-1)
    + alpha(1-alpha)^2 E_(t-2)
    + ...
```

So historical weights decay geometrically:

```text
alpha * (1-alpha)^k
```

For `alpha = 0.35`:

```text
current     0.350
1 old       0.228
2 old       0.148
3 old       0.096
4 old       0.062
...
```

The infinite geometric weights sum to `1`.

This is why the method is called an exponentially weighted moving average.

---

## 14. Severity mapping

Current leak confidence is mapped to severity as:

```text
0  - 29   LOW
30 - 59   MEDIUM
60 - 79   HIGH
80 - 100  CRITICAL
```

These are diagnostic product thresholds, not statistical confidence intervals.

---

## 15. Example weighted calculation

Suppose a mature window produces:

```text
E_heap = 0.80
E_old  = 0.70
E_gc   = unavailable
E_hist = 0.50
```

With all available weights equal to `1`:

```text
E_total = (0.80 + 0.70 + 0.50) / 3
        = 0.6667
```

With full maturity:

```text
instantaneousLeakScore = 66.67
```

If the previous confidence is `40` and `alpha=0.35`:

```text
L_t = 0.35 * 66.67 + 0.65 * 40
    = 49.33
```

The unavailable GC signal does not contribute a zero to the denominator.

---

## 16. What the model does not claim

The current model does **not** claim that:

- a score of 80 means an 80% probability of a leak
- heap growth by itself proves a leak
- `/proc` or JMX counters identify the retaining object graph
- GC reclaim heuristics are equivalent to post-major-GC live-set analysis
- histogram concentration proves the dominant class is the root cause
- CPU or I/O activity is part of the JVM leak score

CPU and I/O use separate runtime-neutral analyzers and separate score labels.

---

## 17. Known limitations and future improvements

Planned improvements include:

- correlate retention with collector-specific major/full-GC events
- inspect post-GC memory floors more directly
- add noise thresholds so tiny positive movements do not count equally with meaningful movement
- add regression/slope features
- track histogram class persistence across multiple historical windows
- validate heuristics against deterministic healthy and leaking workloads
- keep explainability when additional evidence signals are introduced

The architecture should continue to preserve the distinction:

```text
measurement -> normalized evidence -> user-configurable weight -> historical smoothing
```
