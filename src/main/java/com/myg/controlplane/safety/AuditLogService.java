package com.myg.controlplane.safety;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final SafetyAuditRecordJpaRepository repository;

    public AuditLogService(Clock clock,
                           ObjectMapper objectMapper,
                           SafetyAuditRecordJpaRepository repository) {
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SafetyAuditEventType action,
                       AuditResourceType resourceType,
                       String resourceId,
                       String actor,
                       String summary,
                       Map<String, Object> metadata) {
        record(action, resourceType, resourceId, actor, summary, metadata, clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SafetyAuditEventType action,
                       AuditResourceType resourceType,
                       String resourceId,
                       String actor,
                       String summary,
                       Map<String, Object> metadata,
                       Instant recordedAt) {
        String normalizedResourceId = normalizeResourceId(resourceType, resourceId);
        repository.save(new SafetyAuditRecordEntity(
                UUID.randomUUID(),
                action,
                parseRunId(resourceType, normalizedResourceId),
                actor.trim(),
                summary.trim(),
                recordedAt,
                resourceType,
                normalizedResourceId,
                serializeMetadata(metadata == null ? Map.of() : new LinkedHashMap<>(metadata))
        ));
    }

    @Transactional(readOnly = true)
    public List<SafetyAuditRecordResponse> findRecords(Optional<SafetyAuditEventType> action,
                                                       Optional<AuditResourceType> resourceType,
                                                       Optional<String> actor,
                                                       Optional<String> resourceId) {
        return repository.findAllByOrderByRecordedAtDescIdDesc().stream()
                .map(entity -> SafetyAuditRecordResponse.from(entity.toDomain(objectMapper)))
                .filter(record -> action.map(value -> record.action() == value).orElse(true))
                .filter(record -> resourceType.map(value -> record.resourceType() == value).orElse(true))
                .filter(record -> actor.map(value -> record.actor().equalsIgnoreCase(value)).orElse(true))
                .filter(record -> resourceId.map(value -> record.resourceId().equalsIgnoreCase(value)).orElse(true))
                .toList();
    }

    private String normalizeResourceId(AuditResourceType resourceType, String resourceId) {
        if (resourceId != null && !resourceId.isBlank()) {
            return resourceId.trim();
        }
        return resourceType == AuditResourceType.KILL_SWITCH ? "global" : "unknown";
    }

    private UUID parseRunId(AuditResourceType resourceType, String resourceId) {
        if (resourceType != AuditResourceType.RUN) {
            return null;
        }
        return UUID.fromString(resourceId);
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize audit metadata", exception);
        }
    }
}
