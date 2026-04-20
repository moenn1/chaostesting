package com.myg.controlplane.agents.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import java.util.UUID;

public record CreateAgentCommandRequest(
        @NotNull UUID agentId,
        @NotBlank String faultType,
        @NotEmpty Map<@NotBlank String, @NotBlank String> parameters,
        @Positive long durationSeconds,
        @NotBlank String targetScope
) {
}
