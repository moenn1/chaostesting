package com.myg.controlplane.agents.service;

import com.myg.controlplane.agents.domain.AgentStatus;
import com.myg.controlplane.agents.domain.RegisteredAgent;
import com.myg.controlplane.agents.infrastructure.AgentEntity;
import com.myg.controlplane.agents.infrastructure.AgentJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRegistryService {

    private final Clock clock;
    private final AgentRegistryProperties properties;
    private final AgentJpaRepository agentJpaRepository;

    public AgentRegistryService(Clock clock,
                                AgentRegistryProperties properties,
                                AgentJpaRepository agentJpaRepository) {
        this.clock = clock;
        this.properties = properties;
        this.agentJpaRepository = agentJpaRepository;
    }

    @Transactional
    public AgentSnapshot register(String name,
                                  String hostname,
                                  String environment,
                                  String region,
                                  List<String> supportedFaultCapabilities) {
        Instant now = clock.instant();
        AgentEntity agent = new AgentEntity(
                UUID.randomUUID(),
                name.trim(),
                hostname.trim(),
                environment.trim(),
                region.trim(),
                normalizeCapabilities(supportedFaultCapabilities),
                now,
                now
        );
        return snapshot(agentJpaRepository.save(agent).toDomain(), now);
    }

    @Transactional
    public AgentSnapshot heartbeat(UUID agentId) {
        Instant now = clock.instant();
        AgentEntity agent = agentJpaRepository.findById(agentId)
                .orElseThrow(() -> new AgentNotFoundException(agentId));
        agent.setLastHeartbeatAt(now);
        return snapshot(agent.toDomain(), now);
    }

    @Transactional(readOnly = true)
    public List<AgentSnapshot> findAll(Optional<String> environment,
                                       Optional<String> region,
                                       Optional<AgentStatus> status,
                                       Optional<String> capability) {
        Instant now = clock.instant();
        return agentJpaRepository.findAll().stream()
                .map(AgentEntity::toDomain)
                .map(agent -> snapshot(agent, now))
                .filter(snapshot -> environment
                        .map(value -> snapshot.environment().equalsIgnoreCase(value))
                        .orElse(true))
                .filter(snapshot -> region
                        .map(value -> snapshot.region().equalsIgnoreCase(value))
                        .orElse(true))
                .filter(snapshot -> status
                        .map(value -> snapshot.status() == value)
                        .orElse(true))
                .filter(snapshot -> capability
                        .map(value -> snapshot.supportedFaultCapabilities().contains(normalizeCapability(value)))
                        .orElse(true))
                .sorted(Comparator.comparing(AgentSnapshot::name).thenComparing(AgentSnapshot::id))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AgentSnapshot> findById(UUID agentId) {
        Instant now = clock.instant();
        return agentJpaRepository.findById(agentId)
                .map(AgentEntity::toDomain)
                .map(agent -> snapshot(agent, now));
    }

    private AgentSnapshot snapshot(RegisteredAgent agent, Instant now) {
        AgentStatus status = now.isAfter(agent.lastHeartbeatAt().plus(properties.getStaleAfter()))
                ? AgentStatus.STALE
                : AgentStatus.HEALTHY;
        return new AgentSnapshot(
                agent.id(),
                agent.name(),
                agent.hostname(),
                agent.environment(),
                agent.region(),
                agent.supportedFaultCapabilities().stream().sorted().toList(),
                agent.registeredAt(),
                agent.lastHeartbeatAt(),
                status
        );
    }

    private Set<String> normalizeCapabilities(List<String> capabilities) {
        return capabilities.stream()
                .map(this::normalizeCapability)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String normalizeCapability(String capability) {
        return capability.trim().toLowerCase(Locale.ROOT);
    }

    public record AgentSnapshot(
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
    }
}
