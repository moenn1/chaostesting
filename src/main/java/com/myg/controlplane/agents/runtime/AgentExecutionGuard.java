package com.myg.controlplane.agents.runtime;

import com.myg.controlplane.safety.DispatchDecision;
import com.myg.controlplane.safety.DispatchValidationResponse;
import com.myg.controlplane.safety.SafetyGuardrailsService;
import org.springframework.stereotype.Service;

@Service
public class AgentExecutionGuard {

    private final SafetyGuardrailsService safetyGuardrailsService;

    public AgentExecutionGuard(SafetyGuardrailsService safetyGuardrailsService) {
        this.safetyGuardrailsService = safetyGuardrailsService;
    }

    public void verify(AgentExecutionPlan executionPlan) {
        DispatchValidationResponse response = safetyGuardrailsService.validate(executionPlan.toDispatchRequest());
        if (response.decision() != DispatchDecision.ALLOWED) {
            throw new UnsafeExecutionPlanException(response);
        }
    }
}
