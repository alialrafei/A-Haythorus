package com.acorp.jvminsight.snapshotcollection.dto;

import com.acorp.jvminsight.container.dto.PodInfo;
import java.time.Instant;
import java.util.List;

/** Pod-aware history payload used by local and cluster history endpoints. */
public record JvmHistoryResponse(
    PodInfo pod, long pid, Instant timestamp, List<JvmHistorySample> history) {}
