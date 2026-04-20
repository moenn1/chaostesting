package com.myg.controlplane.agents.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PollAgentCommandRequest(@NotNull UUID agentId) {
}
