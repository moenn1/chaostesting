package com.myg.controlplane.experiments;

public record ExperimentRequest(
        String name,
        String description,
        TargetSelector targetSelector,
        FaultConfig faultConfig,
        SafetyRules safetyRules,
        EnvironmentMetadata environmentMetadata
) {
}
