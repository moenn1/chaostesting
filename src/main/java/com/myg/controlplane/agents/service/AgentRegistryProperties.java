package com.myg.controlplane.agents.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent-registry")
public class AgentRegistryProperties {

    private Duration staleAfter = Duration.ofSeconds(30);

    public Duration getStaleAfter() {
        return staleAfter;
    }

    public void setStaleAfter(Duration staleAfter) {
        this.staleAfter = staleAfter;
    }
}
