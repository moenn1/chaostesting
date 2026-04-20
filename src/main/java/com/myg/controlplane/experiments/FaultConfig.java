package com.myg.controlplane.experiments;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record FaultConfig(
        String type,
        Long durationSeconds,
        Map<String, BigDecimal> parameters
) {

    public FaultConfig {
        parameters = parameters == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }
}
