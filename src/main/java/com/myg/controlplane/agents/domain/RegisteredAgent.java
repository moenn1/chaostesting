package com.myg.controlplane.agents.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RegisteredAgent(
        UUID id,
        String name,
        String hostname,
        String environment,
        String region,
        Set<String> supportedFaultCapabilities,
        Instant registeredAt,
        Instant lastHeartbeatAt
) {
    public RegisteredAgent {
        supportedFaultCapabilities = Set.copyOf(supportedFaultCapabilities);
    }

    public RegisteredAgent withLastHeartbeatAt(Instant heartbeatAt) {
        return new RegisteredAgent(
                id,
                name,
                hostname,
                environment,
                region,
                supportedFaultCapabilities,
                registeredAt,
                heartbeatAt
        );
    }
}
