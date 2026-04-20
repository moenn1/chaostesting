package com.myg.controlplane.safety;

import jakarta.validation.constraints.NotBlank;

public record DispatchApprovalRequest(
        @NotBlank String targetEnvironment,
        @NotBlank String approvedBy,
        @NotBlank String reason
) {
}
