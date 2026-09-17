# A-Haythorus

> **A Kubernetes-native runtime observability and intelligent diagnostics platform.**

A-Haythorus is a runtime-aware observability system designed to understand application behavior from the **Kubernetes layer down to the process and runtime layer**.

It combines continuous lightweight telemetry, historical persistence, behavioral analysis, explainable scoring, and cross-signal correlation to identify deeper runtime problems rather than expose raw metrics.
Its peer-to-peer discovery, configurable sharding, and bounded distributed collection enable scalable monitoring without a mandatory central collector.
The runtime-aware architecture supports deep diagnostics and problem investigation, with JVM capabilities today and JFR, JVMTI, and additional runtimes support with the same level planned .

---

## Vision

Build an intelligent, Kubernetes-native runtime observability platform capable of understanding application behavior across multiple runtimes — from operating-system resources to runtime internals — and eventually predicting problems before they become incidents.

```text
Observe → Understand → Diagnose → Predict
```

Across:

```text
Kubernetes
    ↓
Container
    ↓
Process
    ↓
Runtime
    ↓
Application behavior
```

---

## Mission

Provide deep runtime visibility and automated diagnosis directly inside Kubernetes by combining:

- Lightweight continuous telemetry
- Runtime-aware monitoring
- Historical persistence
- Behavioral analysis
- Distributed discovery
- Deterministic sharding
- Cross-signal correlation
- Deep runtime diagnostics

The goal is to move observability from:

```text
"What is happening?"
```

toward:

```text
"Why is it happening?"
```

and eventually:

```text
"What is likely to happen next?"
```

---

# Problem Statement

Modern observability systems provide enormous amounts of telemetry, but engineers still frequently need to determine the root cause manually.

A production incident might initially look like:

```text
Memory ↑
CPU ↑
Latency ↑
```

But the important questions are:

```text
Why?
Which process?
Which runtime?
Is the behavior transient or persistent?
Which component is responsible?
Is the problem getting worse?
What evidence supports the diagnosis?
```

Answering these questions often requires switching between multiple tools:

- Kubernetes metrics
- Container metrics
- `/proc`
- JMX
- JVM memory information
- GC information
- Class histograms
- Profilers
- Runtime-specific tools
- Application logs

A-Haythorus is built around a different approach:

> **Correlate the different layers of runtime behavior instead of treating them as isolated metrics.**

---

# Current Status

A-Haythorus currently has a working JVM-focused implementation with:

- Kubernetes-aware peer discovery
- Deterministic configurable sharding
- Peer-to-peer monitoring
- Bounded concurrent peer collection
- Java Attach API integration
- JMX-based JVM telemetry
- Linux `/proc` process telemetry
- Process CPU and I/O monitoring
- Process/native memory telemetry
- JVM memory and GC monitoring
- Thread and thread CPU monitoring
- JVM class histogram collection
- Memory LeakScore analysis
- Four-component memory leak analysis
- Maturity-aware scoring
- Historical runtime samples
- EWMA-based analysis
- Backend history endpoints
- React/TypeScript monitoring UI

The immediate stabilization work focuses on completing the LeakScore investigation workflow, persistence, histogram search, and UI integration.

JFR, JVMTI, additional runtimes, and predictive analysis are planned extensions rather than current completed functionality.

---

# Architecture

```text
                         KUBERNETES
                             │
                    Kubernetes Control Plane
                             │
                 ┌───────────┴───────────┐
                 │                       │
             Discovery                Sharding
                 │                       │
                 └───────────┬───────────┘
                             │
                      A-Haythorus
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
       Process             Runtime            History
        Layer               Layer               Layer
          │                  │                  │
      /proc/cgroup      ┌────┴────┐        Persistence
          │              │         │             │
          │             JMX       Attach         │
          │              │                        │
          │          JVM telemetry                │
          │                                       │
          └──────────────────┬────────────────────┘
                             │
                             ▼
                      Analysis Engine
                             │
             ┌───────────────┼────────────────┐
             │               │                │
          LeakScore       Correlation       Trends
             │               │                │
          Histogram       Diagnosis       Prediction
             │               │                │
             └───────────────┼────────────────┘
                             │
                             ▼
                            UI
```

---

# Kubernetes-Native

A-Haythorus is not simply a monitoring application deployed into Kubernetes.

**Kubernetes is part of the architecture.**

It uses Kubernetes metadata and the control plane for:

- Runtime discovery
- Pod identity
- Application identity
- Peer discovery
- Sharding
- Cluster topology

There is no mandatory external monitoring control plane.

The monitoring system is designed to operate within the Kubernetes environment.

---

# No Mandatory Centralized Collector

A-Haythorus does not require every runtime to report to a central collector.

Instead, agents can discover and communicate with peers inside Kubernetes.

```text
        ┌─────────────── Kubernetes ───────────────┐
        │                                           │
        │   Pod A ↔ Pod B ↔ Pod C ↔ Pod D         │
        │     │       │       │       │             │
        │    A-H     A-H     A-H     A-H           │
        │                                           │
        └───────────────────────────────────────────┘
```

A centralized collector is therefore not a fundamental architectural dependency.

---

# Deterministic Sharding

A-Haythorus uses configurable deterministic sharding to distribute monitoring responsibility across agents.

```text
Kubernetes metadata
        │
        ▼
ShardKeyResolver
        │
        ▼
Shard algorithm
        │
        ▼
Stable shard ID
        │
        ▼
Targeted discovery
        │
        ▼
Peer collection
```

Supported shard inputs include:

- Namespace
- Pod
- Application
- Node
- Kubernetes labels
- Combinations of fields

The computed shard identity is materialized through Kubernetes Pod metadata.

---

# Bounded Peer Concurrency

Distributed discovery should not mean uncontrolled concurrency.

A-Haythorus combines:

```text
Virtual Threads
       +
Concurrency limits
       ↓
Bounded peer collection
```

Sharding determines **which peers should be queried**, while concurrency control determines **how much work happens simultaneously**.

---

# Process + Runtime Observability

A-Haythorus observes applications from multiple layers.

```text
                Application
                     │
               ┌─────┴─────┐
               │           │
              JVM         Linux
               │           │
              JMX        /proc
               │           │
               └─────┬─────┘
                     ▼
                A-Haythorus
```

This allows the system to distinguish runtime-level behavior from process-level behavior.

---

# Linux Process Telemetry

Current process-level telemetry includes:

- Process CPU
- Process I/O
- RSS
- Anonymous resident memory
- File-backed resident memory
- Shared resident memory
- Process data
- Thread stack mappings
- Swap
- Direct buffers
- Mapped buffers

> **Important:** These measurements are not additive. Direct/mapped buffers and memory mappings can overlap conceptually with process RSS. RSS/VmData should not be interpreted as a complete accounting of all native allocations.

---

# JVM-Aware Observability

For JVM workloads, A-Haythorus integrates directly with the runtime using:

- Java Attach API
- JMX
- JVM runtime information
- Heap information
- Memory pools
- Garbage collection
- Threads
- Thread CPU time
- Buffer pools
- Class histograms
- Deadlock detection

---

# LeakScore

One of the core analysis capabilities is **LeakScore**.

Instead of using a single memory metric such as:

```text
Heap = 85%
```

A-Haythorus analyzes multiple memory behaviors.

```text
                    LeakScore
                        │
        ┌───────────────┼────────────────┐
        │               │                │
        ▼               ▼                ▼
 Heap retention    Old-gen retention   GC reclaim
        │               │                │
        └───────────────┼────────────────┘
                        │
                        ▼
                 Histogram growth
                        │
                        ▼
                  Memory score
```

The four major components are:

1. **Heap retention**
2. **Old-generation retention**
3. **GC reclaim behavior**
4. **Histogram growth**

The objective is to distinguish persistent memory-retention behavior from normal temporary allocation activity.

---

# Maturity-Aware Scoring

A score based on a few seconds of observations should not carry the same evidential weight as a score supported by a longer observation history.

A-Haythorus therefore exposes:

```text
LeakScore
+
Maturity
```

The scoring model is being refined toward combining:

```text
Current evidence
        +
Historical evidence
        +
Evidence confidence
```

rather than treating the current score as an isolated observation.

---

# Historical Persistence

A-Haythorus maintains historical runtime observations rather than relying exclusively on the latest snapshot.

```text
t0 → t1 → t2 → t3 → t4 → ...
 │     │     │     │     │
 ▼     ▼     ▼     ▼     ▼
              History
```

Historical data enables analysis of:

- Growth
- Persistence
- Peaks
- Averages
- Trends
- EWMA
- Runtime maturity
- Memory behavior
- CPU behavior
- I/O behavior

---

# Histogram Analysis

JVM class histograms provide another dimension of memory investigation.

The current implementation exposes the most significant classes, with the immediate roadmap extending this into full histogram search.

The intended workflow is:

```text
LeakScore
    │
    ▼
Histogram growth
    │
    ▼
Growing classes
    │
    ▼
Class search
    │
    ▼
Detailed investigation
```

Planned capabilities include:

- Full histogram acquisition
- Class-name search
- Package filtering
- Instance-count sorting
- Retained-byte sorting
- Growth-based sorting
- Historical growth comparison

---

# Cross-Signal Analysis

A-Haythorus is designed around correlation rather than isolated metrics.

For example:

```text
RSS ↑
+
Heap stable
+
Direct buffers ↑
```

provides a different diagnostic signal from:

```text
Old Gen ↑
+
GC reclaim ↓
+
Histogram growth ↑
```

The analysis engine combines these signals to build a richer picture of runtime behavior.

---

# CPU Analysis

CPU analysis is based on process/runtime history rather than a single instantaneous measurement.

```text
CPU time
    +
Wall-clock time
    +
Available processors
          │
          ▼
CPU utilization
          │
          ▼
Historical behavior
          │
          ▼
Persistence / pressure
```

This allows the system to distinguish a short CPU spike from sustained CPU pressure.

---

# I/O Analysis

A-Haythorus tracks process I/O over time and derives behavioral signals such as:

- Storage throughput
- Read/write activity
- Syscall rates
- Peak activity
- Persistence
- Recent intensity

---

# Sidecar Architecture

A-Haythorus is designed to run alongside the monitored workload.

```text
┌────────────────────────────── Pod ──────────────────────────────┐
│                                                                 │
│  ┌─────────────────────┐       ┌─────────────────────────────┐  │
│  │ Application         │       │ A-Haythorus                 │  │
│  │                     │       │                             │  │
│  │ JVM / Runtime       │◄─────►│ Runtime + Process           │  │
│  │                     │       │ Observability               │  │
│  └─────────────────────┘       └─────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

# Lightweight Continuous Monitoring

The normal monitoring path is designed to remain lightweight:

```text
/proc
  +
cgroups
  +
JMX
  +
runtime metrics
  +
history
  +
analysis
```

More expensive diagnostic mechanisms can be introduced only when the system detects behavior that requires deeper investigation.

---

# Runtime-Neutral Architecture

Although JVM support is the current primary runtime implementation, the core architecture is being designed to support multiple runtimes.

```text
                    A-Haythorus Core
                           │
                  Runtime Adapter
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
         JVM             Python            .NET
          │                │                │
     JMX / JFR /       Runtime-specific  Runtime-specific
        JVMTI             tooling           tooling
```

The common analysis layer is intended to operate on concepts such as:

- CPU
- Memory
- I/O
- Threads
- Runtime events
- Historical state
- Anomalies
- Predictions

---

# Multi-Architecture

A-Haythorus is designed for:

```text
linux/amd64
linux/arm64
```

The Java core remains architecture-independent at the application level.

Future native components such as the JVMTI agent will provide architecture-specific native builds.

---

# Security & Deployment Model

A-Haythorus is designed to operate inside the Kubernetes trust boundary.

The production deployment direction includes:

- Kubernetes-native service discovery
- Namespace-scoped RBAC
- Dedicated service account
- Non-root execution
- RuntimeDefault seccomp
- No privileged mode requirement
- No unnecessary extra Linux capabilities
- Kubernetes NetworkPolicy support
- Ingress/gateway authentication handled by the deployment platform
- Shared process namespace where required for runtime inspection
- Shared `/tmp` where required for JVM Attach communication

The monitoring architecture does not require exposing runtime agents directly to the public internet.

---

# Roadmap

## Phase 1 — LeakScore Investigation & Stabilization

- Validate LeakScore behavior
- Validate the four memory components
- Validate maturity
- Validate EWMA
- Investigate false positives
- Test temporary allocation bursts
- Test persistent memory growth
- Test GC-recovered growth
- Test histogram-driven growth
- Refine current-vs-historical confidence
- Expose all components in the UI
- Stabilize scoring semantics
- Add and strengthen tests

## Phase 2 — Persistence Layer

- Make historical state a first-class backend subsystem
- Persist runtime history
- Stable runtime identity
- Bounded history
- History hydration after UI refresh
- PID-independent runtime identity
- Prepare history for future time-series analysis

## Phase 3 — Sharding + Naming → UI

- Expose shard information
- Expose runtime identity
- Expose Pod/application identity
- Complete discovery → shard → UI flow
- Keep Kubernetes as the topology authority
- Keep discovery logic out of the frontend

## Phase 4 — Process & Native Memory

- Complete process/native memory integration
- Display process memory alongside JVM memory
- Clearly separate JVM memory from process memory
- Improve native-memory investigation

## Phase 4.5 — Histogram Class Search

- Full histogram acquisition
- Class search
- Package/class filtering
- Sorting by retained bytes, instance count, and class name
- Growth-based sorting
- Historical growth comparison
- Connect histogram investigation to LeakScore

## Phase 5 — JFR Integration

Introduce JDK Flight Recorder as the deep JVM diagnostic layer.

Potential capabilities:

- Execution samples
- Stack traces
- Allocation events
- GC events
- Thread activity
- Lock/contention information
- Exceptions
- Class loading
- JVM I/O events

The intended model:

```text
Normal monitoring
      │
      ▼
JMX + /proc + cgroups
      │
      ▼
Anomaly
      │
      ▼
Short JFR recording
      │
      ▼
Deep runtime evidence
```

---

# Stable JVM Platform

At this point A-Haythorus becomes a complete JVM observability and diagnostic platform:

```text
/proc
cgroups
JMX
Attach
Persistence
History
LeakScore
Histogram
CPU analysis
I/O analysis
Process memory
Kubernetes discovery
Sharding
Naming
JFR
```

The platform can then be expanded without destabilizing the core architecture.

---

# Phase 6 — Multi-Runtime Support

The first additional runtime targets are:

## Python

Investigate:

- Python runtime metrics
- Memory analysis
- Thread information
- Profiling
- `tracemalloc`
- Runtime-specific diagnostics

## .NET

Investigate:

- Managed memory
- GC
- Threads
- Runtime metrics
- Profiling
- .NET runtime diagnostics

The common analysis model remains runtime-neutral.

---

# Phase 7 — JVMTI

Introduce a native JVM agent using C/C++.

```text
A-Haythorus
     │
     │ Attach API
     ▼
Target JVM
     │
     ▼
libahaythorus-jvmti.so
     │
     ▼
JVMTI
```

Initial areas of investigation:

- Agent lifecycle
- Dynamic loading
- JVMTI capabilities
- Thread events
- Class events
- Exception events
- VM events
- Deeper JVM diagnostics

Later possibilities include:

- Class instrumentation
- Selective runtime probes
- Method-level diagnostics
- Advanced JVM instrumentation

Native components must support:

```text
linux/amd64
linux/arm64
```

---

# Phase 8 — Cross-Signal Runtime Intelligence

Combine:

```text
Process
+
Container
+
Runtime
+
History
+
JFR
+
JVMTI
```

into unified diagnostic workflows.

Example:

```text
RSS increasing
      │
      ▼
Heap stable
      │
      ▼
Direct buffers increasing
      │
      ▼
LeakScore elevated
      │
      ▼
JFR allocation activity
      │
      ▼
Execution path
      │
      ▼
JVMTI investigation
      │
      ▼
Diagnostic evidence
```

---

# Phase 9 — Time-Series Intelligence & Prediction

Historical runtime data creates the foundation for predictive analysis.

```text
Historical data
       │
       ▼
Time-series analysis
       │
       ├── Trends
       ├── Seasonality
       ├── Change points
       ├── Anomaly detection
       └── Forecasting
       │
       ▼
Future-state estimate
```

The long-term goal is to answer:

```text
"What is happening?"
        ↓
"Why is it happening?"
        ↓
"What happens if this continues?"
```

Predictions should include evidence, forecast horizon, and confidence rather than being presented as certainty.

---

# Technology Direction

## Current Core

- Java
- Java Attach API
- JMX
- Linux `/proc`
- Kubernetes API
- Virtual Threads
- React
- TypeScript

## Planned JVM Deep Diagnostics

- JFR
- JVMTI
- C/C++

## Planned Runtime Support

- Python
- .NET
- Additional runtimes through runtime adapters

## Target Architectures

- AMD64
- ARM64

---

# Design Principles

### Kubernetes-native

Kubernetes is part of the system architecture, not simply the deployment target.

### No mandatory external control plane

The monitoring architecture operates through Kubernetes-native discovery and internal peer communication.

### Runtime-aware

Understand the runtime instead of treating every application as an opaque process.

### Runtime-neutral core

Runtime-specific capabilities stay behind adapters.

### Distributed by design

Avoid a mandatory centralized monitoring collector.

### Historical

A snapshot is not enough to understand runtime behavior.

### Evidence-driven

Analysis should explain why a signal is considered abnormal.

### Escalation-based

Use lightweight telemetry continuously and deeper profiling when required.

### Multi-architecture

AMD64 and ARM64 are first-class deployment targets.

### Extensible

New runtimes and diagnostic mechanisms should be added without rewriting the core.

---

# The Long-Term Model

```text
                         A-HAYTHORUS
                              │
                              ▼
                        OBSERVABILITY
                              │
                     "What is happening?"
                              │
                              ▼
                           ANALYSIS
                              │
                     "Is this abnormal?"
                              │
                              ▼
                        INVESTIGATION
                              │
                      "Why is it happening?"
                              │
                              ▼
                         PREDICTION
                              │
                     "What happens next?"
```

A-Haythorus is ultimately moving toward a system that doesn't merely **collect telemetry**, but builds an understanding of **runtime behavior over time** and uses that understanding to assist with diagnosis and prediction.

---

# Current Priority

The immediate objective is to stabilize the existing JVM platform before expanding into additional runtimes and deeper JVM instrumentation.

```text
LeakScore
   ↓
Persistence
   ↓
Sharding + Naming UI
   ↓
Process / Native Memory
   ↓
Histogram Search
   ↓
JFR
   ↓
Stable JVM Platform
   ↓
Python + .NET
   ↓
JVMTI
   ↓
Cross-runtime Intelligence
   ↓
Time-series Prediction
```

> **Build a stable runtime intelligence foundation first, then expand the depth and breadth of the runtimes it can understand.**
