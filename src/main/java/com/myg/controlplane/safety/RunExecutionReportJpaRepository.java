package com.myg.controlplane.safety;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunExecutionReportJpaRepository extends JpaRepository<RunExecutionReportEntity, UUID> {

    List<RunExecutionReportEntity> findAllByRunIdOrderByReportedAtDescIdDesc(UUID runId);
}
