package com.myg.controlplane.experiments;

import java.util.List;

public record ExperimentValidationResponse(
        String message,
        List<ExperimentFieldError> errors
) {
}
