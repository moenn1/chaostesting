package com.myg.controlplane.experiments;

import com.myg.controlplane.safety.ChaosRun;

public record ManualRunStartResult(
        ChaosRun run,
        boolean created
) {
}
