package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.Map;

public record RunTraceReferenceResponse(
        String traceId,
        String source,
        String displayName,
        ChaosRunStatus status,
        Instant startedAt,
        Instant endedAt,
        int entryCount,
        String endpoint,
        Map<String, Object> summary
) {
}
