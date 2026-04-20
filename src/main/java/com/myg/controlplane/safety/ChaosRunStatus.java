package com.myg.controlplane.safety;

public enum ChaosRunStatus {
    ACTIVE,
    STOP_REQUESTED,
    ROLLED_BACK,
    STOPPED,
    COMPLETED,
    FAILED;

    public boolean canBeStopped() {
        return this == ACTIVE;
    }
}
