package com.myg.controlplane.experiments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myg.controlplane.safety.SafetyGuardrailsProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperimentService {

    private static final Set<String> LATENCY_PARAMETERS = Set.of("latencyMs", "percentage", "jitterMs");
    private static final Set<String> CPU_PARAMETERS = Set.of("cpuLoadPercent");
    private static final Set<String> PACKET_LOSS_PARAMETERS = Set.of("lossPercent", "correlationPercent");
    private static final Set<String> HTTP_ERROR_PARAMETERS = Set.of("statusCode", "percentage");
    private static final Set<String> CONSUMER_PAUSE_PARAMETERS = Set.of("pauseSeconds");
    private static final Set<String> CONNECTION_CHURN_PARAMETERS = Set.of("connectionsPerSecond");

    private final ExperimentJpaRepository experimentJpaRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SafetyGuardrailsProperties safetyGuardrailsProperties;

    public ExperimentService(ExperimentJpaRepository experimentJpaRepository,
                             ObjectMapper objectMapper,
                             Clock clock,
                             SafetyGuardrailsProperties safetyGuardrailsProperties) {
        this.experimentJpaRepository = experimentJpaRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.safetyGuardrailsProperties = safetyGuardrailsProperties;
    }

    @Transactional
    public Experiment create(ExperimentRequest request) {
        NormalizedExperiment normalized = normalizeAndValidate(request);
        Instant now = clock.instant();
        ExperimentEntity entity = new ExperimentEntity(
                UUID.randomUUID(),
                normalized.name(),
                normalized.description(),
                ExperimentEntity.writeJson(objectMapper, normalized.targetSelector()),
                ExperimentEntity.writeJson(objectMapper, normalized.faultConfig()),
                ExperimentEntity.writeJson(objectMapper, normalized.safetyRules()),
                ExperimentEntity.writeJson(objectMapper, normalized.environmentMetadata()),
                now,
                now
        );
        return experimentJpaRepository.save(entity).toDomain(objectMapper);
    }

    @Transactional(readOnly = true)
    public List<Experiment> findAll() {
        return experimentJpaRepository.findAllByOrderByUpdatedAtDescIdDesc().stream()
                .map(entity -> entity.toDomain(objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public Experiment findById(UUID experimentId) {
        return experimentJpaRepository.findById(experimentId)
                .map(entity -> entity.toDomain(objectMapper))
                .orElseThrow(() -> new ExperimentNotFoundException(experimentId));
    }

    @Transactional
    public Experiment update(UUID experimentId, ExperimentRequest request) {
        NormalizedExperiment normalized = normalizeAndValidate(request);
        ExperimentEntity entity = experimentJpaRepository.findById(experimentId)
                .orElseThrow(() -> new ExperimentNotFoundException(experimentId));
        entity.update(
                normalized.name(),
                normalized.description(),
                ExperimentEntity.writeJson(objectMapper, normalized.targetSelector()),
                ExperimentEntity.writeJson(objectMapper, normalized.faultConfig()),
                ExperimentEntity.writeJson(objectMapper, normalized.safetyRules()),
                ExperimentEntity.writeJson(objectMapper, normalized.environmentMetadata()),
                clock.instant()
        );
        return experimentJpaRepository.save(entity).toDomain(objectMapper);
    }

    @Transactional
    public void delete(UUID experimentId) {
        if (!experimentJpaRepository.existsById(experimentId)) {
            throw new ExperimentNotFoundException(experimentId);
        }
        experimentJpaRepository.deleteById(experimentId);
    }

    private NormalizedExperiment normalizeAndValidate(ExperimentRequest request) {
        List<ExperimentFieldError> errors = new ArrayList<>();

        String name = requireText(request == null ? null : request.name(), "name",
                "Provide an experiment name.", errors);
        String description = requireText(request == null ? null : request.description(), "description",
                "Provide an experiment description.", errors);
        TargetSelector targetSelector = normalizeTargetSelector(request == null ? null : request.targetSelector(), errors);
        FaultConfig faultConfig = normalizeFaultConfig(request == null ? null : request.faultConfig(), errors);
        SafetyRules safetyRules = normalizeSafetyRules(request == null ? null : request.safetyRules(), errors);
        EnvironmentMetadata environmentMetadata = normalizeEnvironmentMetadata(
                request == null ? null : request.environmentMetadata(),
                errors
        );

        if (!errors.isEmpty()) {
            throw new ExperimentValidationException(errors);
        }
        return new NormalizedExperiment(name, description, targetSelector, faultConfig, safetyRules, environmentMetadata);
    }

    private TargetSelector normalizeTargetSelector(TargetSelector selector, List<ExperimentFieldError> errors) {
        if (selector == null) {
            errors.add(new ExperimentFieldError(
                    "targetSelector",
                    "Provide target selector details with at least one concrete selector field."
            ));
            return new TargetSelector(null, null, null, Map.of(), List.of());
        }

        String service = normalizeText(selector.service());
        String namespace = normalizeText(selector.namespace());
        String cluster = normalizeText(selector.cluster());
        Map<String, String> labels = normalizeStringMap(selector.labels(), "targetSelector.labels", errors);
        List<String> tags = normalizeStringList(selector.tags(), "targetSelector.tags", errors);

        if (service == null && namespace == null && cluster == null && labels.isEmpty() && tags.isEmpty()) {
            errors.add(new ExperimentFieldError(
                    "targetSelector",
                    "Specify at least one of service, namespace, cluster, labels, or tags."
            ));
        }

        return new TargetSelector(service, namespace, cluster, labels, tags);
    }

    private FaultConfig normalizeFaultConfig(FaultConfig faultConfig, List<ExperimentFieldError> errors) {
        if (faultConfig == null) {
            errors.add(new ExperimentFieldError(
                    "faultConfig",
                    "Provide a faultConfig object with type, durationSeconds, and parameters."
            ));
            return new FaultConfig(null, null, Map.of());
        }

        String type = normalizeText(faultConfig.type());
        Long durationSeconds = faultConfig.durationSeconds();
        Map<String, BigDecimal> parameters = normalizeNumericMap(faultConfig.parameters(), "faultConfig.parameters", errors);

        if (type == null) {
            errors.add(new ExperimentFieldError("faultConfig.type", "Provide a supported fault type."));
        }
        if (durationSeconds == null) {
            errors.add(new ExperimentFieldError("faultConfig.durationSeconds", "Provide a positive duration in seconds."));
        } else if (durationSeconds <= 0) {
            errors.add(new ExperimentFieldError("faultConfig.durationSeconds", "Duration must be greater than zero."));
        } else if (durationSeconds > safetyGuardrailsProperties.getMaxDuration().toSeconds()) {
            errors.add(new ExperimentFieldError(
                    "faultConfig.durationSeconds",
                    "Duration exceeds the configured max of %d seconds."
                            .formatted(safetyGuardrailsProperties.getMaxDuration().toSeconds())
            ));
        }

        if (type != null) {
            validateFaultParameters(type, parameters, errors);
        }

        return new FaultConfig(type, durationSeconds, parameters);
    }

    private void validateFaultParameters(String type,
                                         Map<String, BigDecimal> parameters,
                                         List<ExperimentFieldError> errors) {
        switch (type.toLowerCase(Locale.ROOT)) {
            case "latency" -> {
                rejectUnknownParameters(type, parameters, LATENCY_PARAMETERS, errors);
                requireDecimalRange(parameters, "latencyMs", type, BigDecimal.ONE, new BigDecimal("30000"), errors);
                requireDecimalRange(parameters, "percentage", type, BigDecimal.ONE, new BigDecimal("100"), errors);
                requireDecimalRange(parameters, "jitterMs", type, BigDecimal.ZERO, new BigDecimal("5000"), errors, false);
            }
            case "cpu" -> {
                rejectUnknownParameters(type, parameters, CPU_PARAMETERS, errors);
                requireDecimalRange(parameters, "cpuLoadPercent", type, BigDecimal.ONE, new BigDecimal("100"), errors);
            }
            case "packet_loss" -> {
                rejectUnknownParameters(type, parameters, PACKET_LOSS_PARAMETERS, errors);
                requireDecimalRange(parameters, "lossPercent", type, BigDecimal.ONE, new BigDecimal("100"), errors);
                requireDecimalRange(parameters, "correlationPercent", type, BigDecimal.ZERO, new BigDecimal("100"), errors,
                        false);
            }
            case "http_error" -> {
                rejectUnknownParameters(type, parameters, HTTP_ERROR_PARAMETERS, errors);
                requireWholeNumberRange(parameters, "statusCode", type, 400, 599, errors);
                requireDecimalRange(parameters, "percentage", type, BigDecimal.ONE, new BigDecimal("100"), errors);
            }
            case "consumer_pause" -> {
                rejectUnknownParameters(type, parameters, CONSUMER_PAUSE_PARAMETERS, errors);
                requireWholeNumberRange(parameters, "pauseSeconds", type, 1, 3600, errors);
            }
            case "connection_churn" -> {
                rejectUnknownParameters(type, parameters, CONNECTION_CHURN_PARAMETERS, errors);
                requireWholeNumberRange(parameters, "connectionsPerSecond", type, 1, 10000, errors);
            }
            default -> errors.add(new ExperimentFieldError(
                    "faultConfig.type",
                    "Unsupported fault type '%s'. Supported values: latency, cpu, packet_loss, http_error, consumer_pause, connection_churn."
                            .formatted(type)
            ));
        }
    }

    private SafetyRules normalizeSafetyRules(SafetyRules safetyRules, List<ExperimentFieldError> errors) {
        if (safetyRules == null) {
            errors.add(new ExperimentFieldError(
                    "safetyRules",
                    "Provide safety rules including abortConditions, maxAffectedTargets, approvalRequired, and rollbackMode."
            ));
            return new SafetyRules(List.of(), null, null, null);
        }

        List<String> abortConditions = normalizeStringList(
                safetyRules.abortConditions(),
                "safetyRules.abortConditions",
                errors
        );
        Integer maxAffectedTargets = safetyRules.maxAffectedTargets();
        Boolean approvalRequired = safetyRules.approvalRequired();
        String rollbackMode = normalizeText(safetyRules.rollbackMode());

        if (abortConditions.isEmpty()) {
            errors.add(new ExperimentFieldError(
                    "safetyRules.abortConditions",
                    "Provide at least one abort condition to describe when the experiment must stop."
            ));
        }
        if (maxAffectedTargets == null || maxAffectedTargets <= 0) {
            errors.add(new ExperimentFieldError(
                    "safetyRules.maxAffectedTargets",
                    "maxAffectedTargets must be greater than zero."
            ));
        }
        if (approvalRequired == null) {
            errors.add(new ExperimentFieldError(
                    "safetyRules.approvalRequired",
                    "Specify whether the experiment requires approval."
            ));
        }
        if (rollbackMode == null) {
            errors.add(new ExperimentFieldError(
                    "safetyRules.rollbackMode",
                    "Provide a rollbackMode such as automatic or manual."
            ));
        }

        return new SafetyRules(abortConditions, maxAffectedTargets, approvalRequired, rollbackMode);
    }

    private EnvironmentMetadata normalizeEnvironmentMetadata(EnvironmentMetadata environmentMetadata,
                                                             List<ExperimentFieldError> errors) {
        if (environmentMetadata == null) {
            errors.add(new ExperimentFieldError(
                    "environmentMetadata",
                    "Provide environment metadata including environment and any region or team context."
            ));
            return new EnvironmentMetadata(null, null, null, Map.of());
        }

        String environment = normalizeText(environmentMetadata.environment());
        String region = normalizeText(environmentMetadata.region());
        String team = normalizeText(environmentMetadata.team());
        Map<String, String> labels = normalizeStringMap(environmentMetadata.labels(), "environmentMetadata.labels", errors);

        if (environment == null) {
            errors.add(new ExperimentFieldError(
                    "environmentMetadata.environment",
                    "Provide the target environment for this experiment."
            ));
        }

        return new EnvironmentMetadata(environment, region, team, labels);
    }

    private void rejectUnknownParameters(String faultType,
                                         Map<String, BigDecimal> parameters,
                                         Set<String> allowed,
                                         List<ExperimentFieldError> errors) {
        if (parameters.isEmpty()) {
            errors.add(new ExperimentFieldError(
                    "faultConfig.parameters",
                    "Provide parameters for fault type '%s'. Expected keys: %s."
                            .formatted(faultType, String.join(", ", allowed))
            ));
            return;
        }

        parameters.keySet().stream()
                .filter(parameter -> !allowed.contains(parameter))
                .sorted()
                .forEach(parameter -> errors.add(new ExperimentFieldError(
                        "faultConfig.parameters." + parameter,
                        "Unsupported parameter '%s' for fault type '%s'. Allowed keys: %s."
                                .formatted(parameter, faultType, String.join(", ", allowed))
                )));
    }

    private void requireDecimalRange(Map<String, BigDecimal> parameters,
                                     String key,
                                     String faultType,
                                     BigDecimal minimum,
                                     BigDecimal maximum,
                                     List<ExperimentFieldError> errors) {
        requireDecimalRange(parameters, key, faultType, minimum, maximum, errors, true);
    }

    private void requireDecimalRange(Map<String, BigDecimal> parameters,
                                     String key,
                                     String faultType,
                                     BigDecimal minimum,
                                     BigDecimal maximum,
                                     List<ExperimentFieldError> errors,
                                     boolean required) {
        BigDecimal value = parameters.get(key);
        if (value == null) {
            if (required) {
                errors.add(new ExperimentFieldError(
                        "faultConfig.parameters." + key,
                        "Provide parameter '%s' for fault type '%s'."
                                .formatted(key, faultType)
                ));
            }
            return;
        }
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            errors.add(new ExperimentFieldError(
                    "faultConfig.parameters." + key,
                    "Parameter '%s' for fault type '%s' must be between %s and %s."
                            .formatted(key, faultType, minimum.toPlainString(), maximum.toPlainString())
            ));
        }
    }

    private void requireWholeNumberRange(Map<String, BigDecimal> parameters,
                                         String key,
                                         String faultType,
                                         int minimum,
                                         int maximum,
                                         List<ExperimentFieldError> errors) {
        BigDecimal value = parameters.get(key);
        if (value == null) {
            errors.add(new ExperimentFieldError(
                    "faultConfig.parameters." + key,
                    "Provide parameter '%s' for fault type '%s'."
                            .formatted(key, faultType)
            ));
            return;
        }
        if (value.stripTrailingZeros().scale() > 0) {
            errors.add(new ExperimentFieldError(
                    "faultConfig.parameters." + key,
                    "Parameter '%s' for fault type '%s' must be a whole number."
                            .formatted(key, faultType)
            ));
            return;
        }
        if (value.compareTo(BigDecimal.valueOf(minimum)) < 0 || value.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            errors.add(new ExperimentFieldError(
                    "faultConfig.parameters." + key,
                    "Parameter '%s' for fault type '%s' must be between %d and %d."
                            .formatted(key, faultType, minimum, maximum)
            ));
        }
    }

    private String requireText(String value,
                               String field,
                               String message,
                               List<ExperimentFieldError> errors) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            errors.add(new ExperimentFieldError(field, message));
        }
        return normalized;
    }

    private Map<String, String> normalizeStringMap(Map<String, String> values,
                                                   String fieldPrefix,
                                                   List<ExperimentFieldError> errors) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = normalizeText(key);
            String normalizedValue = normalizeText(value);
            if (normalizedKey == null) {
                errors.add(new ExperimentFieldError(fieldPrefix, "Map keys must not be blank."));
                return;
            }
            if (normalizedValue == null) {
                errors.add(new ExperimentFieldError(
                        fieldPrefix + "." + normalizedKey,
                        "Map values must not be blank."
                ));
                return;
            }
            normalized.put(normalizedKey, normalizedValue);
        });
        return normalized;
    }

    private Map<String, BigDecimal> normalizeNumericMap(Map<String, BigDecimal> values,
                                                        String fieldPrefix,
                                                        List<ExperimentFieldError> errors) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = normalizeText(key);
            if (normalizedKey == null) {
                errors.add(new ExperimentFieldError(fieldPrefix, "Parameter names must not be blank."));
                return;
            }
            if (value == null) {
                errors.add(new ExperimentFieldError(
                        fieldPrefix + "." + normalizedKey,
                        "Parameter values must not be null."
                ));
                return;
            }
            normalized.put(normalizedKey, value);
        });
        return normalized;
    }

    private List<String> normalizeStringList(List<String> values,
                                             String fieldPrefix,
                                             List<ExperimentFieldError> errors) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String value = normalizeText(values.get(index));
            if (value == null) {
                errors.add(new ExperimentFieldError(
                        fieldPrefix + "[" + index + "]",
                        "List items must not be blank."
                ));
                continue;
            }
            normalized.add(value);
        }
        return List.copyOf(new LinkedHashSet<>(normalized));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record NormalizedExperiment(
            String name,
            String description,
            TargetSelector targetSelector,
            FaultConfig faultConfig,
            SafetyRules safetyRules,
            EnvironmentMetadata environmentMetadata
    ) {
    }
}
