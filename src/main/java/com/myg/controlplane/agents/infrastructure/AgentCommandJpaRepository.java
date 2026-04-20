package com.myg.controlplane.agents.infrastructure;

import com.myg.controlplane.agents.domain.AgentCommandStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentCommandJpaRepository extends JpaRepository<AgentCommandEntity, UUID> {

    Optional<AgentCommandEntity> findFirstByAgentIdAndStatusInOrderByCreatedAtAsc(UUID agentId,
                                                                                  Collection<AgentCommandStatus> statuses);
}
