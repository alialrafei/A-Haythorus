package com.acorp.jvminsight.snapshotcollection.dto.analysis;

/** Analysis produced from runtime-neutral process telemetry. */
public record ProcessAnalysisSnapshot(
    AnalysisResult cpu,
    AnalysisResult io) {}
