package com.myg.controlplane.core;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetrySnapshotJpaRepository extends JpaRepository<TelemetrySnapshotEntity, UUID> {

    List<TelemetrySnapshotEntity> findTop100ByExperimentRunIdOrderByCapturedAtDesc(UUID experimentRunId);

    List<TelemetrySnapshotEntity> findTop50ByAgentIdOrderByCapturedAtDesc(UUID agentId);
}
