package com.myg.controlplane.agents.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RegisterAgentRequest(
        @NotBlank String name,
        @NotBlank String hostname,
        @NotBlank String environment,
        @NotBlank String region,
        @NotEmpty List<@NotBlank String> supportedFaultCapabilities
) {
}
