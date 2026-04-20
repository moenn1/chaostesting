package com.myg.controlplane.experiments;

import java.time.Instant;
import java.util.UUID;

public record Experiment(
        UUID id,
        String name,
        String description,
        TargetSelector targetSelector,
        FaultConfig faultConfig,
        SafetyRules safetyRules,
        EnvironmentMetadata environmentMetadata,
        Instant createdAt,
        Instant updatedAt
) {
}
