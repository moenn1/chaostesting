package com.myg.controlplane.agents.infrastructure;

import com.myg.controlplane.agents.domain.AgentCommand;
import com.myg.controlplane.agents.domain.AgentCommandStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "agent_commands")
public class AgentCommandEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private String faultType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agent_command_parameters", joinColumns = @JoinColumn(name = "command_id"))
    @MapKeyColumn(name = "parameter_name")
    @Column(name = "parameter_value", nullable = false)
    private Map<String, String> parameters = new LinkedHashMap<>();

    @Column(nullable = false)
    private long durationSeconds;

    @Column(nullable = false)
    private String targetScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentCommandStatus status;

    @Column(nullable = false)
    private int deliveryCount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant startedAt;

    private Instant finishedAt;

    private Instant lastDeliveredAt;

    @Column(length = 2048)
    private String latestMessage;

    protected AgentCommandEntity() {
    }

    public AgentCommandEntity(UUID id,
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
                              String latestMessage) {
        this.id = id;
        this.agentId = agentId;
        this.faultType = faultType;
        this.parameters = new LinkedHashMap<>(parameters);
        this.durationSeconds = durationSeconds;
        this.targetScope = targetScope;
        this.status = status;
        this.deliveryCount = deliveryCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.lastDeliveredAt = lastDeliveredAt;
        this.latestMessage = latestMessage;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public AgentCommandStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getLatestMessage() {
        return latestMessage;
    }

    public void markDelivered(Instant deliveredAt) {
        deliveryCount += 1;
        lastDeliveredAt = deliveredAt;
        updatedAt = deliveredAt;
    }

    public void setStatus(AgentCommandStatus status) {
        this.status = status;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public void setLatestMessage(String latestMessage) {
        this.latestMessage = latestMessage;
    }

    public AgentCommand toDomain() {
        return new AgentCommand(
                id,
                agentId,
                faultType,
                parameters,
                durationSeconds,
                targetScope,
                status,
                deliveryCount,
                createdAt,
                updatedAt,
                startedAt,
                finishedAt,
                lastDeliveredAt,
                latestMessage
        );
    }
}
