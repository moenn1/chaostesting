package com.myg.controlplane.safety;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/safety")
public class RunDispatchController {

    private final DispatchApprovalService dispatchApprovalService;
    private final SafetyGuardrailsService safetyGuardrailsService;

    public RunDispatchController(DispatchApprovalService dispatchApprovalService,
                                 SafetyGuardrailsService safetyGuardrailsService) {
        this.dispatchApprovalService = dispatchApprovalService;
        this.safetyGuardrailsService = safetyGuardrailsService;
    }

    @PostMapping("/approvals")
    @ResponseStatus(HttpStatus.CREATED)
    public DispatchApprovalResponse createApproval(@Valid @RequestBody DispatchApprovalRequest request) {
        return DispatchApprovalResponse.from(dispatchApprovalService.createApproval(request));
    }

    @PostMapping("/dispatches/validate")
    public DispatchValidationResponse validate(@Valid @RequestBody RunDispatchRequest request) {
        return safetyGuardrailsService.validate(request);
    }

    @PostMapping("/dispatches")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DispatchAuthorizationResponse authorize(@Valid @RequestBody RunDispatchRequest request) {
        return safetyGuardrailsService.authorize(request);
    }

    @ExceptionHandler(RunDispatchRejectedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public DispatchValidationResponse handleRejected(RunDispatchRejectedException exception) {
        return exception.getResponse();
    }
}
