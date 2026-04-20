package com.myg.controlplane.agents.runtime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "agent.runtime")
public class AgentRuntimeProperties {

    private boolean enabled;

    @NotNull
    private URI controlPlaneBaseUrl = URI.create("http://localhost:8080");

    @NotBlank
    private String name = "local-agent";

    @NotBlank
    private String hostname = "localhost";

    @NotBlank
    private String environment = "local";

    @NotBlank
    private String region = "local";

    @NotEmpty
    private List<String> supportedFaultCapabilities = List.of("latency", "http_error");

    @NotNull
    private Duration heartbeatInterval = Duration.ofSeconds(10);

    @NotNull
    private Duration commandPollInterval = Duration.ofSeconds(5);

    @NotNull
    private Path registrationFile = Path.of("./data/agent-runtime/registration.json");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getControlPlaneBaseUrl() {
        return controlPlaneBaseUrl;
    }

    public void setControlPlaneBaseUrl(URI controlPlaneBaseUrl) {
        this.controlPlaneBaseUrl = controlPlaneBaseUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public List<String> getSupportedFaultCapabilities() {
        return supportedFaultCapabilities;
    }

    public void setSupportedFaultCapabilities(List<String> supportedFaultCapabilities) {
        this.supportedFaultCapabilities = supportedFaultCapabilities == null
                ? List.of()
                : List.copyOf(supportedFaultCapabilities);
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public Duration getCommandPollInterval() {
        return commandPollInterval;
    }

    public void setCommandPollInterval(Duration commandPollInterval) {
        this.commandPollInterval = commandPollInterval;
    }

    public Path getRegistrationFile() {
        return registrationFile;
    }

    public void setRegistrationFile(Path registrationFile) {
        this.registrationFile = registrationFile;
    }
}
