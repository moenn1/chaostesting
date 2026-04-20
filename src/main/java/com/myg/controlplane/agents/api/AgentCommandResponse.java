package com.myg.controlplane.agents.api;

import com.myg.controlplane.agents.domain.AgentCommandStatus;
import com.myg.controlplane.agents.service.AgentCommandService.AgentCommandSnapshot;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AgentCommandResponse(
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
    public AgentCommandResponse {
        parameters = Map.copyOf(parameters);
    }

    public static AgentCommandResponse from(AgentCommandSnapshot snapshot) {
        return new AgentCommandResponse(
                snapshot.id(),
                snapshot.agentId(),
                snapshot.faultType(),
                snapshot.parameters(),
                snapshot.durationSeconds(),
                snapshot.targetScope(),
                snapshot.status(),
                snapshot.deliveryCount(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.startedAt(),
                snapshot.finishedAt(),
                snapshot.lastDeliveredAt(),
                snapshot.latestMessage()
        );
    }
}
