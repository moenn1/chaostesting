package com.myg.controlplane.agents.runtime;

import com.myg.controlplane.safety.RunDispatchRequest;
import java.util.List;
import java.util.UUID;

public record AgentExecutionPlan(
        UUID dispatchId,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        long requestedDurationSeconds,
        Integer latencyMilliseconds,
        Integer latencyJitterMilliseconds,
        Integer latencyMinimumMilliseconds,
        Integer latencyMaximumMilliseconds,
        Integer errorCode,
        Integer trafficPercentage,
        Integer dropPercentage,
        List<String> routeFilters,
        UUID approvalId
) {
    public AgentExecutionPlan {
        routeFilters = routeFilters == null ? List.of() : List.copyOf(routeFilters);
    }

    RunDispatchRequest toDispatchRequest() {
        return new RunDispatchRequest(
                targetEnvironment,
                targetSelector,
                faultType,
                requestedDurationSeconds,
                latencyMilliseconds,
                latencyJitterMilliseconds,
                latencyMinimumMilliseconds,
                latencyMaximumMilliseconds,
                errorCode,
                trafficPercentage,
                dropPercentage,
                routeFilters,
                approvalId,
                "agent-runtime"
        );
    }
}
