package com.myg.controlplane.experiments;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record EnvironmentMetadata(
        String environment,
        String region,
        String team,
        Map<String, String> labels
) {

    public EnvironmentMetadata {
        labels = labels == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(labels));
    }
}
