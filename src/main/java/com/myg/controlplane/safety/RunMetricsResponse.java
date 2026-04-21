package com.myg.controlplane.safety;

import java.util.List;
import java.util.UUID;

public record RunMetricsResponse(
        UUID runId,
        ChaosRunStatus status,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        Integer latencyMilliseconds,
        Integer latencyJitterMilliseconds,
        Integer latencyMinimumMilliseconds,
        Integer latencyMaximumMilliseconds,
        Integer errorCode,
        Integer trafficPercentage,
        Integer dropPercentage,
        List<String> routeFilters,
        RunMetricsSummaryResponse summary,
        List<RunMetricPointResponse> series
) {
    public RunMetricsResponse {
        routeFilters = routeFilters == null ? List.of() : List.copyOf(routeFilters);
    }
}
