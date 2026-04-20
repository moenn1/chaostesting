package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RunLifecycleEvent(
        UUID id,
        UUID runId,
        RunLifecycleEventType eventType,
        String actor,
        String summary,
        Map<String, Object> details,
        Instant recordedAt
) {
}
