package com.myg.controlplane.safety;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "run_lifecycle_events")
public class RunLifecycleEventEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunLifecycleEventType eventType;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, length = 512)
    private String summary;

    @Lob
    @Column(nullable = false)
    private String detailsJson;

    @Column(nullable = false)
    private Instant recordedAt;

    protected RunLifecycleEventEntity() {
    }

    public RunLifecycleEventEntity(UUID id,
                                   UUID runId,
                                   RunLifecycleEventType eventType,
                                   String actor,
                                   String summary,
                                   String detailsJson,
                                   Instant recordedAt) {
        this.id = id;
        this.runId = runId;
        this.eventType = eventType;
        this.actor = actor;
        this.summary = summary;
        this.detailsJson = detailsJson;
        this.recordedAt = recordedAt;
    }

    public RunLifecycleEvent toDomain(ObjectMapper objectMapper) {
        return new RunLifecycleEvent(
                id,
                runId,
                eventType,
                actor,
                summary,
                readJson(objectMapper, detailsJson),
                recordedAt
        );
    }

    public static String writeJson(ObjectMapper objectMapper, Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize run lifecycle event details", exception);
        }
    }

    private static Map<String, Object> readJson(ObjectMapper objectMapper, String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize run lifecycle event details", exception);
        }
    }
}
