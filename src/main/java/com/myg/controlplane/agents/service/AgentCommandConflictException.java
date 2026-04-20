package com.myg.controlplane.agents.service;

public class AgentCommandConflictException extends RuntimeException {

    public AgentCommandConflictException(String message) {
        super(message);
    }
}
