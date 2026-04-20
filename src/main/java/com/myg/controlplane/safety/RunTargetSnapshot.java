package com.myg.controlplane.safety;

import com.myg.controlplane.experiments.EnvironmentMetadata;
import com.myg.controlplane.experiments.TargetSelector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record RunTargetSnapshot(
        List<String> services,
        TargetSelector selector,
        EnvironmentMetadata environment,
        List<RunAssignedAgent> assignedAgents
) {

    public RunTargetSnapshot {
        services = services == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(services));
        assignedAgents = assignedAgents == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(assignedAgents));
    }
}
