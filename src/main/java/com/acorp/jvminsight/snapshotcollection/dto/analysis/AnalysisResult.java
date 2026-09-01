package com.acorp.jvminsight.snapshotcollection.dto.analysis;

import java.util.List;
import java.util.Map;

/**
 * Runtime-neutral analyzer output.
 *
 * <p>The meaning of {@code score} is named explicitly by {@code scoreLabel}; analyzers must not
 * pretend every domain score is a probability or a failure score.
 */
public record AnalysisResult(
    String domain,
    String scoreLabel,
    double score,
    List<EvidenceSignal> evidence,
    Map<String, Double> metrics,
    List<String> reasons) {}
