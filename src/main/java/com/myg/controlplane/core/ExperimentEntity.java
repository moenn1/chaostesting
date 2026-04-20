package com.myg.controlplane.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "experiments",
        uniqueConstraints = @UniqueConstraint(name = "uk_experiments_slug", columnNames = "slug")
)
public class ExperimentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 160)
    private String slug;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT")
    private String hypothesis;

    @Column(nullable = false, length = 128)
    private String targetEnvironment;

    @Column(nullable = false)
    private String targetSelector;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant archivedAt;

    protected ExperimentEntity() {
    }

    public ExperimentEntity(UUID id,
                            String name,
                            String slug,
                            String hypothesis,
                            String targetEnvironment,
                            String targetSelector,
                            String createdBy,
                            Instant createdAt,
                            Instant updatedAt,
                            Instant archivedAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.hypothesis = hypothesis;
        this.targetEnvironment = targetEnvironment;
        this.targetSelector = targetSelector;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archivedAt = archivedAt;
    }
}
