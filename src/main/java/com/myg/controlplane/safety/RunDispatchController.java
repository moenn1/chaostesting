package com.myg.controlplane.safety;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/safety")
public class RunDispatchController {

    private final ChaosRunService chaosRunService;
    private final DispatchApprovalService dispatchApprovalService;
    private final KillSwitchService killSwitchService;
    private final SafetyGuardrailsService safetyGuardrailsService;

    public RunDispatchController(ChaosRunService chaosRunService,
                                 DispatchApprovalService dispatchApprovalService,
                                 KillSwitchService killSwitchService,
                                 SafetyGuardrailsService safetyGuardrailsService) {
        this.chaosRunService = chaosRunService;
        this.dispatchApprovalService = dispatchApprovalService;
        this.killSwitchService = killSwitchService;
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
        return chaosRunService.createRun(request);
    }

    @GetMapping("/runs")
    public List<ChaosRunResponse> listRuns(@RequestParam Optional<String> status) {
        return chaosRunService.findAll(status.map(this::parseRunStatus));
    }

    @GetMapping("/kill-switch")
    public KillSwitchStatusResponse getKillSwitchStatus() {
        return killSwitchService.getStatus();
    }

    @PostMapping("/kill-switch/enable")
    public KillSwitchStatusResponse enableKillSwitch(@Valid @RequestBody KillSwitchCommandRequest request) {
        return killSwitchService.enable(request);
    }

    @PostMapping("/kill-switch/disable")
    public KillSwitchStatusResponse disableKillSwitch(@Valid @RequestBody KillSwitchCommandRequest request) {
        return killSwitchService.disable(request);
    }

    @ExceptionHandler(RunDispatchRejectedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public DispatchValidationResponse handleRejected(RunDispatchRejectedException exception) {
        return exception.getResponse();
    }

    private ChaosRunStatus parseRunStatus(String rawStatus) {
        try {
            return ChaosRunStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported run status filter: " + rawStatus);
        }
    }
}
