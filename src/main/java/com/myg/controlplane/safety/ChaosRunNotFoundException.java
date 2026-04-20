package com.myg.controlplane.safety;

import java.util.UUID;

public class ChaosRunNotFoundException extends RuntimeException {

    public ChaosRunNotFoundException(UUID runId) {
        super("Chaos run not found: " + runId);
    }
}
