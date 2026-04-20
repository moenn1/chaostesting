package com.myg.controlplane.safety;

import com.myg.controlplane.agents.domain.AgentStatus;
import com.myg.controlplane.agents.service.AgentRegistryService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record RunAssignedAgent(
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

    public RunAssignedAgent {
        supportedFaultCapabilities = supportedFaultCapabilities == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(supportedFaultCapabilities));
    }

    public static RunAssignedAgent from(AgentRegistryService.AgentSnapshot snapshot) {
        return new RunAssignedAgent(
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
