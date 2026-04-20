package com.myg.controlplane.safety;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chaos.latency")
public class LatencyInjectionProperties {

    @NotNull
    private Duration maxLatency = Duration.ofSeconds(5);

    @NotNull
    private Duration telemetryInterval = Duration.ofSeconds(30);

    public Duration getMaxLatency() {
        return maxLatency;
    }

    public void setMaxLatency(Duration maxLatency) {
        this.maxLatency = maxLatency;
    }

    public Duration getTelemetryInterval() {
        return telemetryInterval;
    }

    public void setTelemetryInterval(Duration telemetryInterval) {
        this.telemetryInterval = telemetryInterval;
    }
}
