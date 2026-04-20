package com.myg.controlplane.core;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaultInjectionActionJpaRepository extends JpaRepository<FaultInjectionActionEntity, UUID> {

    List<FaultInjectionActionEntity> findAllByExperimentRunIdOrderByRequestedAtAsc(UUID experimentRunId);

    List<FaultInjectionActionEntity> findAllByAgentIdOrderByRequestedAtDesc(UUID agentId);
}
