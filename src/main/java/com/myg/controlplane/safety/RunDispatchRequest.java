package com.myg.controlplane.safety;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.UUID;

public record RunDispatchRequest(
        @NotBlank String targetEnvironment,
        @NotBlank String targetSelector,
        @NotBlank String faultType,
        @Positive long requestedDurationSeconds,
        UUID approvalId
) {
    public Duration requestedDuration() {
        return Duration.ofSeconds(requestedDurationSeconds);
    }
}
