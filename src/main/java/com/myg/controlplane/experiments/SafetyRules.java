package com.myg.controlplane.experiments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SafetyRules(
        List<String> abortConditions,
        Integer maxAffectedTargets,
        Boolean approvalRequired,
        String rollbackMode
) {

    public SafetyRules {
        abortConditions = abortConditions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(abortConditions));
    }
}
