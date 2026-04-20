package com.myg.controlplane.core;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {

    List<AuditEventEntity> findAllByExperimentRunIdOrderByOccurredAtAsc(UUID experimentRunId);

    List<AuditEventEntity> findTop100ByExperimentIdOrderByOccurredAtDesc(UUID experimentId);

    List<AuditEventEntity> findTop100ByAgentIdOrderByOccurredAtDesc(UUID agentId);
}
