package com.myg.controlplane.agents.domain;

public enum AgentCommandStatus {
    ASSIGNED,
    RECEIVED,
    RUNNING,
    STOP_REQUESTED,
    STOPPED,
    COMPLETED,
    FAILED
}
