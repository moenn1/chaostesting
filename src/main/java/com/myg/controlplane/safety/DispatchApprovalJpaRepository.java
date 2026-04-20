package com.myg.controlplane.safety;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchApprovalJpaRepository extends JpaRepository<DispatchApprovalEntity, UUID> {
}
