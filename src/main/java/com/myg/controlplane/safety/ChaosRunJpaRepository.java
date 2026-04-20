package com.myg.controlplane.safety;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChaosRunJpaRepository extends JpaRepository<ChaosRunEntity, UUID> {

    List<ChaosRunEntity> findAllByOrderByCreatedAtDescIdDesc();

    List<ChaosRunEntity> findAllByStatusOrderByCreatedAtDescIdDesc(ChaosRunStatus status);

    List<ChaosRunEntity> findAllByStatus(ChaosRunStatus status);

    long countByStatus(ChaosRunStatus status);

    Optional<ChaosRunEntity> findFirstByExperimentIdAndStatusOrderByStartedAtDescIdDesc(UUID experimentId,
                                                                                        ChaosRunStatus status);
}
