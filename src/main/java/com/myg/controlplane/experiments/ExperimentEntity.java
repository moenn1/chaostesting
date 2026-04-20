package com.myg.controlplane.experiments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "StructuredExperimentEntity")
@Table(name = "experiment_definitions")
public class ExperimentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Lob
    @Column(nullable = false)
    private String targetSelectorJson;

    @Lob
    @Column(nullable = false)
    private String faultConfigJson;

    @Lob
    @Column(nullable = false)
    private String safetyRulesJson;

    @Lob
    @Column(nullable = false)
    private String environmentMetadataJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ExperimentEntity() {
    }

    public ExperimentEntity(UUID id,
                            String name,
                            String description,
                            String targetSelectorJson,
                            String faultConfigJson,
                            String safetyRulesJson,
                            String environmentMetadataJson,
                            Instant createdAt,
                            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.targetSelectorJson = targetSelectorJson;
        this.faultConfigJson = faultConfigJson;
        this.safetyRulesJson = safetyRulesJson;
        this.environmentMetadataJson = environmentMetadataJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void update(String name,
                       String description,
                       String targetSelectorJson,
                       String faultConfigJson,
                       String safetyRulesJson,
                       String environmentMetadataJson,
                       Instant updatedAt) {
        this.name = name;
        this.description = description;
        this.targetSelectorJson = targetSelectorJson;
        this.faultConfigJson = faultConfigJson;
        this.safetyRulesJson = safetyRulesJson;
        this.environmentMetadataJson = environmentMetadataJson;
        this.updatedAt = updatedAt;
    }

    public Experiment toDomain(ObjectMapper objectMapper) {
        return new Experiment(
                id,
                name,
                description,
                readJson(objectMapper, targetSelectorJson, TargetSelector.class),
                readJson(objectMapper, faultConfigJson, FaultConfig.class),
                readJson(objectMapper, safetyRulesJson, SafetyRules.class),
                readJson(objectMapper, environmentMetadataJson, EnvironmentMetadata.class),
                createdAt,
                updatedAt
        );
    }

    public static String writeJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize experiment payload", exception);
        }
    }

    private static <T> T readJson(ObjectMapper objectMapper, String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize experiment payload", exception);
        }
    }
}
