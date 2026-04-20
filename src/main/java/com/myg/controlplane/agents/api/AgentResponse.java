package com.myg.controlplane.agents.api;

import com.myg.controlplane.agents.domain.AgentStatus;
import com.myg.controlplane.agents.service.AgentRegistryService.AgentSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentResponse(
        UUID id,
        String name,
        String hostname,
        String environment,
        String region,
        List<String> supportedFaultCapabilities,
        Instant registeredAt,
        Instant lastHeartbeatAt,
        AgentStatus status
) {
    public static AgentResponse from(AgentSnapshot snapshot) {
        return new AgentResponse(
                snapshot.id(),
                snapshot.name(),
                snapshot.hostname(),
                snapshot.environment(),
                snapshot.region(),
                snapshot.supportedFaultCapabilities(),
                snapshot.registeredAt(),
                snapshot.lastHeartbeatAt(),
                snapshot.status()
        );
    }
}
