package com.myg.controlplane.agents.runtime;

import com.myg.controlplane.safety.RunDispatchRequest;
import java.util.UUID;

public record AgentExecutionPlan(
        UUID dispatchId,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        long requestedDurationSeconds,
        Integer latencyMilliseconds,
        Integer trafficPercentage,
        UUID approvalId
) {
    RunDispatchRequest toDispatchRequest() {
        return new RunDispatchRequest(
                targetEnvironment,
                targetSelector,
                faultType,
                requestedDurationSeconds,
                latencyMilliseconds,
                trafficPercentage,
                approvalId,
                "agent-runtime"
        );
    }
}
