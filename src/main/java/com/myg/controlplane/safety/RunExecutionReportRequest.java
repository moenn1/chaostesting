package com.myg.controlplane.safety;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RunExecutionReportRequest(
        @NotNull RunExecutionReportState state,
        @NotBlank String reportedBy,
        @NotBlank String message
) {
}
