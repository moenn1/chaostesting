package com.myg.controlplane.safety;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

public record RunDispatchRequest(
        @NotBlank String targetEnvironment,
        @NotBlank String targetSelector,
        @NotBlank String faultType,
        @Positive long requestedDurationSeconds,
        Integer errorCode,
        @Positive @Max(100) Integer trafficPercentage,
        List<@NotBlank String> routeFilters,
        UUID approvalId,
        @NotBlank String requestedBy
) {
    public RunDispatchRequest {
        routeFilters = routeFilters == null
                ? List.of()
                : routeFilters.stream()
                .map(value -> value == null ? null : value.trim())
                .toList();
    }

    public Duration requestedDuration() {
        return Duration.ofSeconds(requestedDurationSeconds);
    }

    public String normalizedFaultType() {
        return faultType.trim().toLowerCase(Locale.ROOT);
    }
}
