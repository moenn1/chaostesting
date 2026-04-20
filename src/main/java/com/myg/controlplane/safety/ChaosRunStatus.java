package com.myg.controlplane.safety;

public enum ChaosRunStatus {
    ACTIVE,
    STOP_REQUESTED,
    STOPPED,
    COMPLETED;

    public boolean canBeStopped() {
        return this == ACTIVE;
    }
}
