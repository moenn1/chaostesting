package com.myg.controlplane.safety;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chaos.guardrails")
public class SafetyGuardrailsProperties {

    @NotNull
    private EnvironmentPolicyMode environmentPolicyMode = EnvironmentPolicyMode.ALLOWLIST;

    @NotEmpty
    private List<String> controlledEnvironments = List.of("local", "dev", "staging", "prod", "prod-shadow");

    @NotEmpty
    private List<String> productionLikeEnvironments = List.of("prod", "production", "prod-shadow");

    @NotNull
    private Duration maxDuration = Duration.ofMinutes(15);

    @NotNull
    private Duration approvalTtl = Duration.ofMinutes(30);

    public EnvironmentPolicyMode getEnvironmentPolicyMode() {
        return environmentPolicyMode;
    }

    public void setEnvironmentPolicyMode(EnvironmentPolicyMode environmentPolicyMode) {
        this.environmentPolicyMode = environmentPolicyMode;
    }

    public List<String> getControlledEnvironments() {
        return controlledEnvironments;
    }

    public void setControlledEnvironments(List<String> controlledEnvironments) {
        this.controlledEnvironments = controlledEnvironments == null ? List.of() : List.copyOf(controlledEnvironments);
    }

    public List<String> getProductionLikeEnvironments() {
        return productionLikeEnvironments;
    }

    public void setProductionLikeEnvironments(List<String> productionLikeEnvironments) {
        this.productionLikeEnvironments = productionLikeEnvironments == null
                ? List.of()
                : List.copyOf(productionLikeEnvironments);
    }

    public Duration getMaxDuration() {
        return maxDuration;
    }

    public void setMaxDuration(Duration maxDuration) {
        this.maxDuration = maxDuration;
    }

    public Duration getApprovalTtl() {
        return approvalTtl;
    }

    public void setApprovalTtl(Duration approvalTtl) {
        this.approvalTtl = approvalTtl;
    }

    Set<String> normalizedControlledEnvironments() {
        return normalizeAll(controlledEnvironments);
    }

    Set<String> normalizedProductionLikeEnvironments() {
        return normalizeAll(productionLikeEnvironments);
    }

    String normalizeEnvironment(String environment) {
        return environment.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> normalizeAll(List<String> values) {
        return values.stream()
                .map(this::normalizeEnvironment)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
