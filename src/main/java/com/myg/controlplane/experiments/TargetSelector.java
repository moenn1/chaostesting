package com.myg.controlplane.experiments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TargetSelector(
        String service,
        String namespace,
        String cluster,
        Map<String, String> labels,
        List<String> tags
) {

    public TargetSelector {
        labels = labels == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(labels));
        tags = tags == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(tags));
    }
}
