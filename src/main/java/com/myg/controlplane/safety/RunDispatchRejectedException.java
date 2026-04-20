package com.myg.controlplane.safety;

public class RunDispatchRejectedException extends RuntimeException {

    private final DispatchValidationResponse response;

    public RunDispatchRejectedException(DispatchValidationResponse response) {
        super("Run dispatch rejected: " + response.decision());
        this.response = response;
    }

    public DispatchValidationResponse getResponse() {
        return response;
    }
}
