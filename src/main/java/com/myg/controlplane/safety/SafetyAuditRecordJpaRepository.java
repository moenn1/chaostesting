package com.myg.controlplane.safety;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyAuditRecordJpaRepository extends JpaRepository<SafetyAuditRecordEntity, UUID> {

    List<SafetyAuditRecordEntity> findAllByOrderByRecordedAtDescIdDesc();
}
