package com.myg.controlplane.experiments;

import java.util.UUID;

public class ExperimentNotFoundException extends RuntimeException {

    public ExperimentNotFoundException(UUID experimentId) {
        super("Experiment not found: " + experimentId);
    }
}
