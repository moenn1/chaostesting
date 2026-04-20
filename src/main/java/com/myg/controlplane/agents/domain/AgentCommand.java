package com.myg.controlplane.agents.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AgentCommand(
        UUID id,
        UUID agentId,
        String faultType,
        Map<String, String> parameters,
        long durationSeconds,
        String targetScope,
        AgentCommandStatus status,
        int deliveryCount,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant finishedAt,
        Instant lastDeliveredAt,
        String latestMessage
) {
    public AgentCommand {
        parameters = Map.copyOf(new LinkedHashMap<>(parameters));
    }
}
