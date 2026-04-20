package com.myg.controlplane.safety;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LatencyTelemetrySnapshotJpaRepository extends JpaRepository<LatencyTelemetrySnapshotEntity, UUID> {

    List<LatencyTelemetrySnapshotEntity> findAllByRunIdOrderByCapturedAtDescIdDesc(UUID runId);
}
