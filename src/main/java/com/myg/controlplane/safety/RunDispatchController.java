package com.myg.controlplane.safety;

import com.myg.controlplane.security.CurrentSecurityActor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    private final ChaosRunDispatchService chaosRunDispatchService;
    private final ChaosRunService chaosRunService;
    private final DispatchApprovalService dispatchApprovalService;
    private final KillSwitchService killSwitchService;
    private final SafetyGuardrailsService safetyGuardrailsService;
    private final CurrentSecurityActor currentSecurityActor;

    public RunDispatchController(ChaosRunDispatchService chaosRunDispatchService,
                                 ChaosRunService chaosRunService,
                                 DispatchApprovalService dispatchApprovalService,
                                 KillSwitchService killSwitchService,
                                 SafetyGuardrailsService safetyGuardrailsService,
                                 CurrentSecurityActor currentSecurityActor) {
        this.chaosRunDispatchService = chaosRunDispatchService;
        this.chaosRunService = chaosRunService;
        this.dispatchApprovalService = dispatchApprovalService;
        this.killSwitchService = killSwitchService;
        this.safetyGuardrailsService = safetyGuardrailsService;
        this.currentSecurityActor = currentSecurityActor;
    }

    @PostMapping("/approvals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('chaos.approve')")
    public DispatchApprovalResponse createApproval(@Valid @RequestBody DispatchApprovalRequest request,
                                                   Authentication authentication) {
        return DispatchApprovalResponse.from(
                dispatchApprovalService.createApproval(currentSecurityActor.username(authentication), request)
        );
    }

    @PostMapping("/dispatches/validate")
    @PreAuthorize("hasAuthority('chaos.operate')")
    public DispatchValidationResponse validate(@Valid @RequestBody RunDispatchRequest request) {
        return safetyGuardrailsService.validate(request);
    }

    @PostMapping("/dispatches")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('chaos.operate')")
    public DispatchAuthorizationResponse authorize(@Valid @RequestBody RunDispatchRequest request,
                                                   Authentication authentication) {
        return chaosRunDispatchService.createRun(currentSecurityActor.username(authentication), request);
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('chaos.view')")
    public List<ChaosRunResponse> listRuns(@RequestParam Optional<String> status) {
        return chaosRunService.findAll(status.map(this::parseRunStatus));
    }

    @GetMapping("/runs/{runId}")
    @PreAuthorize("hasAuthority('chaos.view')")
    public ChaosRunResponse getRun(@PathVariable UUID runId) {
        return chaosRunService.getRun(runId);
    }

    @GetMapping("/runs/{runId}/telemetry")
    @PreAuthorize("hasAuthority('chaos.view')")
    public List<LatencyTelemetrySnapshotResponse> listTelemetry(@PathVariable UUID runId) {
        return chaosRunService.findTelemetry(runId);
    }

    @PostMapping("/runs/{runId}/stop")
    @PreAuthorize("hasAuthority('chaos.operate')")
    public ChaosRunResponse stopRun(@PathVariable UUID runId,
                                    @Valid @RequestBody RunStopRequest request,
                                    Authentication authentication) {
        return chaosRunService.stopRun(runId, currentSecurityActor.username(authentication), request.reason());
    }

    @GetMapping("/kill-switch")
    @PreAuthorize("hasAuthority('chaos.view')")
    public KillSwitchStatusResponse getKillSwitchStatus() {
        return killSwitchService.getStatus();
    }

    @PostMapping("/kill-switch/enable")
    @PreAuthorize("hasAuthority('chaos.admin')")
    public KillSwitchStatusResponse enableKillSwitch(@Valid @RequestBody KillSwitchCommandRequest request,
                                                     Authentication authentication) {
        return killSwitchService.enable(currentSecurityActor.username(authentication), request);
    }

    @PostMapping("/kill-switch/disable")
    @PreAuthorize("hasAuthority('chaos.admin')")
    public KillSwitchStatusResponse disableKillSwitch(@Valid @RequestBody KillSwitchCommandRequest request,
                                                      Authentication authentication) {
        return killSwitchService.disable(currentSecurityActor.username(authentication), request);
    }

    @ExceptionHandler(RunDispatchRejectedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public DispatchValidationResponse handleRejected(RunDispatchRejectedException exception) {
        return exception.getResponse();
    }

    @ExceptionHandler(ChaosRunNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleRunNotFound() {
    }

    @ExceptionHandler(RunStopRejectedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public RunStopValidationResponse handleRunStopRejected(RunStopRejectedException exception) {
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
