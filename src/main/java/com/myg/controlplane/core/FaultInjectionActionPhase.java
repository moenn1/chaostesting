package com.myg.controlplane.core;

public enum FaultInjectionActionPhase {
    PLANNED,
    DISPATCHED,
    RUNNING,
    STOP_REQUESTED,
    ROLLED_BACK,
    FAILED
}
