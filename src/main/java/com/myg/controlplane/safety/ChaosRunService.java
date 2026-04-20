package com.myg.controlplane.safety;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChaosRunService {

    private final ChaosRunJpaRepository chaosRunJpaRepository;
    private final SafetyGuardrailsService safetyGuardrailsService;

    public ChaosRunService(ChaosRunJpaRepository chaosRunJpaRepository,
                           SafetyGuardrailsService safetyGuardrailsService) {
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.safetyGuardrailsService = safetyGuardrailsService;
    }

    @Transactional
    public DispatchAuthorizationResponse createRun(RunDispatchRequest request) {
        DispatchAuthorizationResponse authorization = safetyGuardrailsService.authorize(request);
        ChaosRunEntity entity = new ChaosRunEntity(
                authorization.dispatchId(),
                authorization.targetEnvironment(),
                authorization.targetSelector(),
                authorization.faultType(),
                authorization.requestedDurationSeconds(),
                authorization.approvalId(),
                ChaosRunStatus.ACTIVE,
                authorization.authorizedAt(),
                null,
                null,
                null
        );
        chaosRunJpaRepository.save(entity);
        return authorization;
    }

    @Transactional(readOnly = true)
    public List<ChaosRunResponse> findAll(Optional<ChaosRunStatus> status) {
        List<ChaosRunEntity> entities = status
                .map(chaosRunJpaRepository::findAllByStatusOrderByCreatedAtDescIdDesc)
                .orElseGet(chaosRunJpaRepository::findAllByOrderByCreatedAtDescIdDesc);
        return entities.stream()
                .map(ChaosRunEntity::toDomain)
                .map(ChaosRunResponse::from)
                .toList();
    }
}
