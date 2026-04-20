package com.myg.controlplane.agents.runtime;

import com.myg.controlplane.agents.api.AgentCommandResponse;

public interface FaultExecutionEngine {

    FaultExecutionHandle apply(AgentCommandResponse command);
}
