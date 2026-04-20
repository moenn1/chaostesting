package com.myg.controlplane.experiments;

import java.util.List;

public class ExperimentValidationException extends RuntimeException {

    private final List<ExperimentFieldError> errors;

    public ExperimentValidationException(List<ExperimentFieldError> errors) {
        super("Experiment validation failed");
        this.errors = List.copyOf(errors);
    }

    public List<ExperimentFieldError> getErrors() {
        return errors;
    }
}
