package com.myg.controlplane.safety;

import java.util.List;
import java.util.UUID;

public record RunStopValidationResponse(
        String code,
        String message,
        UUID runId,
        ChaosRunStatus currentStatus,
        List<ChaosRunStatus> stoppableStatuses
) {
}
