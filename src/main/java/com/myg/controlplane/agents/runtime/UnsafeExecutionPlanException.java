package com.myg.controlplane.agents.runtime;

import com.myg.controlplane.safety.DispatchValidationResponse;

public class UnsafeExecutionPlanException extends RuntimeException {

    private final DispatchValidationResponse response;

    public UnsafeExecutionPlanException(DispatchValidationResponse response) {
        super("Unsafe execution plan: " + response.decision());
        this.response = response;
    }

    public DispatchValidationResponse getResponse() {
        return response;
    }
}
