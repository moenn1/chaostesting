package com.myg.controlplane.experiments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StructuredExperimentJpaRepository extends JpaRepository<ExperimentEntity, UUID> {

    List<ExperimentEntity> findAllByOrderByUpdatedAtDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select experiment from StructuredExperimentEntity experiment where experiment.id = :experimentId")
    Optional<ExperimentEntity> findByIdForUpdate(@Param("experimentId") UUID experimentId);
}
