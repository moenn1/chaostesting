package com.myg.controlplane.core;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentRunJpaRepository extends JpaRepository<ExperimentRunEntity, UUID> {

    List<ExperimentRunEntity> findAllByExperimentIdOrderByCreatedAtDesc(UUID experimentId);

    List<ExperimentRunEntity> findAllByStatusOrderByCreatedAtDesc(ExperimentRunStatus status);

    List<ExperimentRunEntity> findTop50ByLeadAgentIdOrderByCreatedAtDesc(UUID leadAgentId);
}
