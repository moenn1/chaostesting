package com.myg.controlplane.safety;

import java.util.List;
import java.util.UUID;

public record RunTraceResponse(
        UUID runId,
        List<RunTraceReferenceResponse> traces
) {
}
