package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DispatchAuthorizationResponse(
        UUID dispatchId,
        String status,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        long requestedDurationSeconds,
        Integer errorCode,
        Integer trafficPercentage,
        List<String> routeFilters,
        UUID approvalId,
        Instant authorizedAt
) {
    public DispatchAuthorizationResponse {
        routeFilters = routeFilters == null ? List.of() : List.copyOf(routeFilters);
    }
}
