package com.myg.controlplane.safety;

import jakarta.validation.constraints.NotBlank;

public record KillSwitchCommandRequest(
        @NotBlank String operator,
        @NotBlank String reason
) {
}
