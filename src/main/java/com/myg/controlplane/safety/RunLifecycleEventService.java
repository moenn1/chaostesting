package com.myg.controlplane.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunLifecycleEventService {

    private final ObjectMapper objectMapper;
    private final RunLifecycleEventJpaRepository repository;

    public RunLifecycleEventService(ObjectMapper objectMapper,
                                    RunLifecycleEventJpaRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRunStarted(ChaosRun run, String actor, String experimentName) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("experimentId", run.experimentId() == null ? null : run.experimentId().toString());
        details.put("experimentName", experimentName);
        details.put("targetEnvironment", run.targetEnvironment());
        details.put("targetSelector", run.targetSelector());
        details.put("services", run.targetSnapshot() == null ? java.util.List.of() : run.targetSnapshot().services());
        details.put("assignedAgentIds", run.targetSnapshot() == null
                ? java.util.List.of()
                : run.targetSnapshot().assignedAgents().stream().map(RunAssignedAgent::id).map(UUID::toString).toList());
        repository.save(new RunLifecycleEventEntity(
                UUID.randomUUID(),
                run.id(),
                RunLifecycleEventType.RUN_STARTED,
                actor,
                "Manual run started for experiment " + experimentName,
                RunLifecycleEventEntity.writeJson(objectMapper, details),
                run.startedAt()
        ));
    }
}
