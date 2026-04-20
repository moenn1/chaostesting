package com.myg.controlplane.agents.infrastructure;

import com.myg.controlplane.agents.domain.RegisteredAgent;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "agents")
public class AgentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String hostname;

    @Column(nullable = false)
    private String environment;

    @Column(nullable = false)
    private String region;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agent_capabilities", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "capability", nullable = false)
    private Set<String> supportedFaultCapabilities = new LinkedHashSet<>();

    @Column(nullable = false)
    private Instant registeredAt;

    @Column(nullable = false)
    private Instant lastHeartbeatAt;

    protected AgentEntity() {
    }

    public AgentEntity(UUID id,
                       String name,
                       String hostname,
                       String environment,
                       String region,
                       Set<String> supportedFaultCapabilities,
                       Instant registeredAt,
                       Instant lastHeartbeatAt) {
        this.id = id;
        this.name = name;
        this.hostname = hostname;
        this.environment = environment;
        this.region = region;
        this.supportedFaultCapabilities = new LinkedHashSet<>(supportedFaultCapabilities);
        this.registeredAt = registeredAt;
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public RegisteredAgent toDomain() {
        return new RegisteredAgent(
                id,
                name,
                hostname,
                environment,
                region,
                supportedFaultCapabilities,
                registeredAt,
                lastHeartbeatAt
        );
    }
}
