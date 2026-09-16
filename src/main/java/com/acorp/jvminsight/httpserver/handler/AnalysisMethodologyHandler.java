package com.acorp.jvminsight.httpserver.handler;

import com.acorp.jvminsight.httpserver.util.JsonResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Exposes human-readable explanations for runtime analysis metrics and illustrative examples. */
public final class AnalysisMethodologyHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      JsonResponse.methodNotAllowed(exchange);
      return;
    }

    Map<String, Object> response = Map.of(
        "title", "A-Haythorus analysis methodology",
        "version", 1,
        "sections", List.of(
            Map.of(
                "id", "leak-score",
                "title", "Memory leak score",
                "description", "An explainable estimate of memory-retention evidence. It is not the probability of a leak, the percentage of heap leaked, or a prediction of OOM.",
                "signals", List.of(
                    Map.of("id", "heap-retention", "name", "Heap retention", "description", "Measures how consistently heap usage moves upward across observed intervals and how strongly the positive growth is concentrated."),
                    Map.of("id", "old-gen-retention", "name", "Old-generation retention", "description", "Applies the same retention evidence to the old-generation memory pool when that pool is available."),
                    Map.of("id", "gc-reclaim", "name", "GC reclaim", "description", "Measures how much of the observed positive heap growth is subsequently reclaimed by garbage collection. Lower reclaim means stronger retention evidence."),
                    Map.of("id", "histogram-growth", "name", "Object histogram growth", "description", "Uses positive class-level byte growth and concentration of that growth as additional retention evidence when histogram data is available.")),
                "formula", "E_memory = weighted average of the available heap-retention, old-generation-retention, GC-reclaim, and histogram-growth evidence.",
                "scoreFlow", List.of(
                    "Current-window evidence is scaled by observation maturity.",
                    "Historical confidence is smoothed with EWMA.",
                    "Unavailable signals are excluded rather than treated as zero.",
                    "Configured weights are relative; setting a weight to zero disables that signal.")),
            Map.of(
                "id", "heap-growth",
                "title", "Heap growth",
                "description", "A change in heap used between observations. Positive growth means the later observation is higher; it does not mean that many bytes were allocated because garbage collection can reclaim memory between observations.",
                "formula", "heapDelta = currentHeapUsed - previousHeapUsed"),
            Map.of(
                "id", "heap-persistence",
                "title", "Heap-growth persistence",
                "description", "The fraction of observed intervals whose heap usage increased relative to the previous sample.",
                "formula", "persistence = positiveIntervals / totalIntervals"),
            Map.of(
                "id", "gc-reclaim",
                "title", "GC reclaim evidence",
                "description", "Compares bytes reclaimed by garbage collection with positive heap growth. GC activity alone is not evidence of a leak; the signal is about growth that remains unreclaimed.",
                "formula", "reclaimRatio = reclaimedBytes / positiveGrowthBytes"),
            Map.of(
                "id", "maturity",
                "title", "Historical maturity",
                "description", "Prevents a score from immediately behaving like a fully observed analysis window. The score gains influence as the configured observation window fills.",
                "formula", "maturity = min(1, observedSeconds / configuredWindowSeconds)"),
            Map.of(
                "id", "ewma",
                "title", "Historical confidence",
                "description", "The primary historical score is smoothed so a single transient observation has less influence than repeated evidence.",
                "formula", "L_t = alpha * E_t + (1 - alpha) * L_(t-1)")),
        "examples", List.of(
            Map.of(
                "id", "healthy-gc",
                "title", "Healthy allocation and GC",
                "description", "Heap rises after allocations and repeatedly falls after GC. Retention evidence should remain limited because growth is being reclaimed.",
                "samples", List.of(
                    Map.of("time", "00:00", "heapUsedMb", 512, "gcReclaimedMb", 0),
                    Map.of("time", "00:10", "heapUsedMb", 540, "gcReclaimedMb", 24),
                    Map.of("time", "00:20", "heapUsedMb", 518, "gcReclaimedMb", 31),
                    Map.of("time", "00:30", "heapUsedMb", 545, "gcReclaimedMb", 26))),
            Map.of(
                "id", "retention",
                "title", "Gradual retained memory",
                "description", "Heap and old-generation usage continue rising while GC reclaims only a small fraction of positive growth. This creates stronger retention evidence.",
                "samples", List.of(
                    Map.of("time", "00:00", "heapUsedMb", 500, "gcReclaimedMb", 0),
                    Map.of("time", "00:10", "heapUsedMb", 535, "gcReclaimedMb", 4),
                    Map.of("time", "00:20", "heapUsedMb", 571, "gcReclaimedMb", 3),
                    Map.of("time", "00:30", "heapUsedMb", 608, "gcReclaimedMb", 5))),
            Map.of(
                "id", "burst",
                "title", "Transient allocation burst",
                "description", "A large short-lived allocation can create a large pairwise delta without representing a persistent leak. Historical persistence and maturity are important context.",
                "samples", List.of(
                    Map.of("time", "00:00", "heapUsedMb", 500, "gcReclaimedMb", 0),
                    Map.of("time", "00:10", "heapUsedMb", 820, "gcReclaimedMb", 0),
                    Map.of("time", "00:20", "heapUsedMb", 510, "gcReclaimedMb", 310),
                    Map.of("time", "00:30", "heapUsedMb", 525, "gcReclaimedMb", 12)))));

    JsonResponse.ok(exchange, response);
  }
}
