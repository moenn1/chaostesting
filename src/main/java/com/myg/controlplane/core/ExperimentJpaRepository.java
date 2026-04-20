package com.myg.controlplane.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentJpaRepository extends JpaRepository<ExperimentEntity, UUID> {

    Optional<ExperimentEntity> findBySlug(String slug);

    List<ExperimentEntity> findAllByArchivedAtIsNullOrderByUpdatedAtDesc();

    List<ExperimentEntity> findAllByTargetEnvironmentAndArchivedAtIsNullOrderByUpdatedAtDesc(String targetEnvironment);
}
