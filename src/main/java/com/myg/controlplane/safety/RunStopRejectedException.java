package com.myg.controlplane.safety;

public class RunStopRejectedException extends RuntimeException {

    private final RunStopValidationResponse response;

    public RunStopRejectedException(RunStopValidationResponse response) {
        super(response.message());
        this.response = response;
    }

    public RunStopValidationResponse getResponse() {
        return response;
    }
}
