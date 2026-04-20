package com.myg.controlplane.agents.service;

import java.util.UUID;

public class AgentCommandNotFoundException extends RuntimeException {

    public AgentCommandNotFoundException(UUID commandId) {
        super("Agent command not found: " + commandId);
    }
}
