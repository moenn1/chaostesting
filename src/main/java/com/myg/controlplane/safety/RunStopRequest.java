package com.myg.controlplane.safety;

import jakarta.validation.constraints.NotBlank;

public record RunStopRequest(
        @NotBlank String operator,
        @NotBlank String reason
) {
}
