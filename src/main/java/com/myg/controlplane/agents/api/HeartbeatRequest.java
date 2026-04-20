package com.myg.controlplane.agents.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record HeartbeatRequest(@NotNull UUID agentId) {
}
