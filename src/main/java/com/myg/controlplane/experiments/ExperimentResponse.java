package com.myg.controlplane.experiments;

import java.time.Instant;
import java.util.UUID;

public record ExperimentResponse(
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

    public static ExperimentResponse from(Experiment experiment) {
        return new ExperimentResponse(
                experiment.id(),
                experiment.name(),
                experiment.description(),
                experiment.targetSelector(),
                experiment.faultConfig(),
                experiment.safetyRules(),
                experiment.environmentMetadata(),
                experiment.createdAt(),
                experiment.updatedAt()
        );
    }
}
