package com.myg.controlplane.experiments;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentJpaRepository extends JpaRepository<ExperimentEntity, UUID> {

    List<ExperimentEntity> findAllByOrderByUpdatedAtDescIdDesc();
}
