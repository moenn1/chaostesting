package com.myg.controlplane.agents.service;

import com.myg.controlplane.agents.domain.AgentCommand;
import com.myg.controlplane.agents.domain.AgentCommandStatus;
import com.myg.controlplane.agents.infrastructure.AgentCommandEntity;
import com.myg.controlplane.agents.infrastructure.AgentCommandJpaRepository;
import com.myg.controlplane.agents.infrastructure.AgentJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentCommandService {

    private static final Set<AgentCommandStatus> REPORTABLE_STATUSES = EnumSet.of(
            AgentCommandStatus.RECEIVED,
            AgentCommandStatus.RUNNING,
            AgentCommandStatus.STOPPED,
            AgentCommandStatus.COMPLETED,
            AgentCommandStatus.FAILED
    );
    private static final Set<AgentCommandStatus> TERMINAL_STATUSES = EnumSet.of(
            AgentCommandStatus.STOPPED,
            AgentCommandStatus.COMPLETED,
            AgentCommandStatus.FAILED
    );

    private final Clock clock;
    private final AgentJpaRepository agentJpaRepository;
    private final AgentCommandJpaRepository agentCommandJpaRepository;

    public AgentCommandService(Clock clock,
                               AgentJpaRepository agentJpaRepository,
                               AgentCommandJpaRepository agentCommandJpaRepository) {
        this.clock = clock;
        this.agentJpaRepository = agentJpaRepository;
        this.agentCommandJpaRepository = agentCommandJpaRepository;
    }

    @Transactional
    public AgentCommandSnapshot assign(UUID agentId,
                                       String faultType,
                                       Map<String, String> parameters,
                                       long durationSeconds,
                                       String targetScope) {
        ensureAgentExists(agentId);
        Instant now = clock.instant();
        AgentCommandEntity entity = new AgentCommandEntity(
                UUID.randomUUID(),
                agentId,
                normalizeToken(faultType),
                normalizeParameters(parameters),
                durationSeconds,
                targetScope.trim(),
                AgentCommandStatus.ASSIGNED,
                0,
                now,
                now,
                null,
                null,
                null,
                null
        );
        return snapshot(agentCommandJpaRepository.save(entity).toDomain());
    }

    @Transactional
    public Optional<AgentCommandSnapshot> poll(UUID agentId) {
        ensureAgentExists(agentId);
        Instant now = clock.instant();
        Optional<AgentCommandEntity> nextCommand = findNextCommand(agentId);
        nextCommand.ifPresent(command -> command.markDelivered(now));
        return nextCommand.map(AgentCommandEntity::toDomain).map(this::snapshot);
    }

    @Transactional(readOnly = true)
    public AgentCommandSnapshot findById(UUID commandId) {
        return snapshot(agentCommandJpaRepository.findById(commandId)
                .orElseThrow(() -> new AgentCommandNotFoundException(commandId))
                .toDomain());
    }

    @Transactional
    public AgentCommandSnapshot reportExecution(UUID agentId,
                                                UUID commandId,
                                                AgentCommandStatus status,
                                                String message) {
        if (!REPORTABLE_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unsupported execution status: " + status);
        }

        AgentCommandEntity entity = agentCommandJpaRepository.findById(commandId)
                .orElseThrow(() -> new AgentCommandNotFoundException(commandId));
        ensureAgentOwnsCommand(entity, agentId);

        AgentCommandStatus currentStatus = entity.getStatus();
        if (!currentStatus.equals(status) && !isTransitionAllowed(currentStatus, status)) {
            throw new AgentCommandConflictException(
                    "Cannot transition command %s from %s to %s".formatted(commandId, currentStatus, status)
            );
        }

        applyStatus(entity, status, message, clock.instant());
        return snapshot(entity.toDomain());
    }

    @Transactional
    public AgentCommandSnapshot requestStop(UUID commandId) {
        AgentCommandEntity entity = agentCommandJpaRepository.findById(commandId)
                .orElseThrow(() -> new AgentCommandNotFoundException(commandId));
        AgentCommandStatus currentStatus = entity.getStatus();
        if (TERMINAL_STATUSES.contains(currentStatus) || currentStatus == AgentCommandStatus.STOP_REQUESTED) {
            return snapshot(entity.toDomain());
        }
        if (currentStatus != AgentCommandStatus.ASSIGNED
                && currentStatus != AgentCommandStatus.RECEIVED
                && currentStatus != AgentCommandStatus.RUNNING) {
            throw new AgentCommandConflictException(
                    "Cannot request stop for command %s in state %s".formatted(commandId, currentStatus)
            );
        }
        applyStatus(entity, AgentCommandStatus.STOP_REQUESTED, "Stop requested by control plane", clock.instant());
        return snapshot(entity.toDomain());
    }

    private Optional<AgentCommandEntity> findNextCommand(UUID agentId) {
        List<List<AgentCommandStatus>> priorityBuckets = List.of(
                List.of(AgentCommandStatus.STOP_REQUESTED),
                List.of(AgentCommandStatus.RUNNING, AgentCommandStatus.RECEIVED),
                List.of(AgentCommandStatus.ASSIGNED)
        );
        for (List<AgentCommandStatus> statuses : priorityBuckets) {
            Optional<AgentCommandEntity> match = agentCommandJpaRepository
                    .findFirstByAgentIdAndStatusInOrderByCreatedAtAsc(agentId, statuses);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private void ensureAgentExists(UUID agentId) {
        if (!agentJpaRepository.existsById(agentId)) {
            throw new AgentNotFoundException(agentId);
        }
    }

    private void ensureAgentOwnsCommand(AgentCommandEntity entity, UUID agentId) {
        if (!entity.getAgentId().equals(agentId)) {
            throw new AgentCommandConflictException(
                    "Command %s is not assigned to agent %s".formatted(entity.toDomain().id(), agentId)
            );
        }
    }

    private boolean isTransitionAllowed(AgentCommandStatus currentStatus, AgentCommandStatus newStatus) {
        return switch (currentStatus) {
            case ASSIGNED -> newStatus == AgentCommandStatus.RECEIVED
                    || newStatus == AgentCommandStatus.RUNNING
                    || newStatus == AgentCommandStatus.STOPPED
                    || newStatus == AgentCommandStatus.FAILED;
            case RECEIVED -> newStatus == AgentCommandStatus.RUNNING
                    || newStatus == AgentCommandStatus.STOPPED
                    || newStatus == AgentCommandStatus.FAILED;
            case RUNNING -> newStatus == AgentCommandStatus.STOPPED
                    || newStatus == AgentCommandStatus.COMPLETED
                    || newStatus == AgentCommandStatus.FAILED;
            case STOP_REQUESTED -> newStatus == AgentCommandStatus.STOPPED
                    || newStatus == AgentCommandStatus.COMPLETED
                    || newStatus == AgentCommandStatus.FAILED;
            case STOPPED, COMPLETED, FAILED -> false;
        };
    }

    private void applyStatus(AgentCommandEntity entity,
                             AgentCommandStatus status,
                             String message,
                             Instant now) {
        entity.setStatus(status);
        entity.setUpdatedAt(now);
        entity.setLatestMessage(normalizeMessage(message, entity.getLatestMessage()));
        if (status == AgentCommandStatus.RUNNING && entity.getStartedAt() == null) {
            entity.setStartedAt(now);
        }
        if (TERMINAL_STATUSES.contains(status) && entity.getFinishedAt() == null) {
            entity.setFinishedAt(now);
        }
    }

    private String normalizeToken(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, String> normalizeParameters(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().trim(),
                        entry -> entry.getValue().trim(),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    private String normalizeMessage(String message, String existingMessage) {
        if (message == null || message.isBlank()) {
            return existingMessage;
        }
        return message.trim();
    }

    private AgentCommandSnapshot snapshot(AgentCommand command) {
        return new AgentCommandSnapshot(
                command.id(),
                command.agentId(),
                command.faultType(),
                command.parameters(),
                command.durationSeconds(),
                command.targetScope(),
                command.status(),
                command.deliveryCount(),
                command.createdAt(),
                command.updatedAt(),
                command.startedAt(),
                command.finishedAt(),
                command.lastDeliveredAt(),
                command.latestMessage()
        );
    }

    public record AgentCommandSnapshot(
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
    }
}
