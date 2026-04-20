package com.myg.controlplane.safety;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

public record RunDispatchRequest(
        @NotBlank String targetEnvironment,
        @NotBlank String targetSelector,
        @NotBlank String faultType,
        @Positive long requestedDurationSeconds,
        @PositiveOrZero Integer latencyMilliseconds,
        @PositiveOrZero Integer latencyJitterMilliseconds,
        @PositiveOrZero Integer latencyMinimumMilliseconds,
        @PositiveOrZero Integer latencyMaximumMilliseconds,
        @PositiveOrZero Integer trafficPercentage,
        @PositiveOrZero Integer dropPercentage,
        UUID approvalId,
        @NotBlank String requestedBy
) {
    public Duration requestedDuration() {
        return Duration.ofSeconds(requestedDurationSeconds);
    }

    public String normalizedFaultType() {
        return faultType.trim().toLowerCase(Locale.ROOT);
    }
}
