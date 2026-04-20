package com.myg.controlplane.safety;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunLifecycleEventJpaRepository extends JpaRepository<RunLifecycleEventEntity, UUID> {

    List<RunLifecycleEventEntity> findAllByRunIdOrderByRecordedAtAscIdAsc(UUID runId);
}
