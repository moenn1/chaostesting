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
        Integer errorCode,
        Integer trafficPercentage,
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
                errorCode,
                trafficPercentage,
                routeFilters,
                approvalId,
                "agent-runtime"
        );
    }
}
