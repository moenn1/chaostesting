package com.myg.controlplane.experiments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myg.controlplane.agents.domain.AgentStatus;
import com.myg.controlplane.agents.service.AgentRegistryService;
import com.myg.controlplane.safety.ChaosRun;
import com.myg.controlplane.safety.ChaosRunJpaRepository;
import com.myg.controlplane.safety.ChaosRunStatus;
import com.myg.controlplane.safety.ChaosRunService;
import com.myg.controlplane.safety.DispatchAuthorizationResponse;
import com.myg.controlplane.safety.RunAssignedAgent;
import com.myg.controlplane.safety.RunDispatchRequest;
import com.myg.controlplane.safety.RunTargetSnapshot;
import com.myg.controlplane.safety.SafetyGuardrailsService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperimentRunService {

    private final StructuredExperimentJpaRepository experimentJpaRepository;
    private final ObjectMapper objectMapper;
    private final AgentRegistryService agentRegistryService;
    private final ChaosRunJpaRepository chaosRunJpaRepository;
    private final SafetyGuardrailsService safetyGuardrailsService;
    private final ChaosRunService chaosRunService;

    public ExperimentRunService(StructuredExperimentJpaRepository experimentJpaRepository,
                                ObjectMapper objectMapper,
                                AgentRegistryService agentRegistryService,
                                ChaosRunJpaRepository chaosRunJpaRepository,
                                SafetyGuardrailsService safetyGuardrailsService,
                                ChaosRunService chaosRunService) {
        this.experimentJpaRepository = experimentJpaRepository;
        this.objectMapper = objectMapper;
        this.agentRegistryService = agentRegistryService;
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.safetyGuardrailsService = safetyGuardrailsService;
        this.chaosRunService = chaosRunService;
    }

    @Transactional
    public ManualRunStartResult startManualRun(UUID experimentId, String requestedBy, UUID approvalId) {
        Experiment experiment = experimentJpaRepository.findByIdForUpdate(experimentId)
                .map(entity -> entity.toDomain(objectMapper))
                .orElseThrow(() -> new ExperimentNotFoundException(experimentId));

        Optional<ChaosRun> existingRun = chaosRunJpaRepository
                .findFirstByExperimentIdAndStatusOrderByStartedAtDescIdDesc(experimentId, ChaosRunStatus.ACTIVE)
                .map(entity -> entity.toDomain(objectMapper));
        if (existingRun.isPresent()) {
            return new ManualRunStartResult(existingRun.get(), false);
        }

        RunTargetSnapshot targetSnapshot = resolveTargetSnapshot(experiment);
        String faultType = experiment.faultConfig().type();
        RunDispatchRequest request = new RunDispatchRequest(
                experiment.environmentMetadata().environment(),
                describeTargetSelector(experiment.targetSelector()),
                faultType,
                experiment.faultConfig().durationSeconds(),
                "latency".equals(faultType) ? integerParameter(experiment.faultConfig().parameters(), "latencyMs") : null,
                "http_error".equals(faultType) ? integerParameter(experiment.faultConfig().parameters(), "statusCode") : null,
                integerParameter(experiment.faultConfig().parameters(), "percentage"),
                List.of(),
                approvalId,
                requestedBy.trim()
        );
        DispatchAuthorizationResponse authorization = safetyGuardrailsService.authorize(requestedBy, request);
        ChaosRun run = chaosRunService.createManualRun(
                requestedBy,
                experiment,
                authorization,
                targetSnapshot
        );
        return new ManualRunStartResult(run, true);
    }

    private RunTargetSnapshot resolveTargetSnapshot(Experiment experiment) {
        EnvironmentMetadata environmentMetadata = experiment.environmentMetadata();
        List<RunAssignedAgent> assignedAgents = agentRegistryService.findAll(
                        Optional.of(environmentMetadata.environment()),
                        Optional.ofNullable(environmentMetadata.region()),
                        Optional.of(AgentStatus.HEALTHY),
                        Optional.of(experiment.faultConfig().type())
                ).stream()
                .map(RunAssignedAgent::from)
                .toList();

        if (assignedAgents.isEmpty()) {
            throw new ManualRunStartException(
                    "No healthy registered agents matched environment '%s', region '%s', and fault capability '%s'."
                            .formatted(
                                    environmentMetadata.environment(),
                                    environmentMetadata.region() == null ? "*" : environmentMetadata.region(),
                                    experiment.faultConfig().type()
                            )
            );
        }

        List<String> services = experiment.targetSelector().service() == null
                ? List.of()
                : List.of(experiment.targetSelector().service());
        return new RunTargetSnapshot(
                services,
                experiment.targetSelector(),
                environmentMetadata,
                assignedAgents
        );
    }

    private String describeTargetSelector(TargetSelector selector) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "service", selector.service());
        addPart(parts, "namespace", selector.namespace());
        addPart(parts, "cluster", selector.cluster());
        selector.labels().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> parts.add("label:" + entry.getKey() + "=" + entry.getValue()));
        if (!selector.tags().isEmpty()) {
            parts.add("tags=" + selector.tags().stream().sorted().reduce((left, right) -> left + "|" + right).orElse(""));
        }
        return String.join(", ", parts);
    }

    private void addPart(List<String> parts, String key, String value) {
        if (value != null) {
            parts.add(key + "=" + value);
        }
    }

    private Integer integerParameter(Map<String, BigDecimal> parameters, String key) {
        BigDecimal value = parameters.get(key);
        return value == null ? null : value.intValue();
    }
}
