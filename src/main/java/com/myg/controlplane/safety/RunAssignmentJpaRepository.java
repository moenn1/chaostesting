package com.myg.controlplane.safety;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunAssignmentJpaRepository extends JpaRepository<RunAssignmentEntity, UUID> {

    List<RunAssignmentEntity> findAllByRunId(UUID runId);

    List<RunAssignmentEntity> findAllByRunIdIn(Collection<UUID> runIds);
}
