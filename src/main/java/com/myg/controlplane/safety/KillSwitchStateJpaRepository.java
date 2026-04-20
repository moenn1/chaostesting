package com.myg.controlplane.safety;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KillSwitchStateJpaRepository extends JpaRepository<KillSwitchStateEntity, Long> {
}
