package com.myg.controlplane.agents.runtime;

import com.myg.controlplane.agents.api.AgentCommandResponse;
import org.springframework.stereotype.Component;

@Component
public class NoOpFaultExecutionEngine implements FaultExecutionEngine {

    @Override
    public FaultExecutionHandle apply(AgentCommandResponse command) {
        return () -> {
        };
    }
}
