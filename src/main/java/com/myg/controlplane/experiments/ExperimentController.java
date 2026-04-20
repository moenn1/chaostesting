package com.myg.controlplane.experiments;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentService experimentService;

    public ExperimentController(ExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('chaos.operate')")
    public ExperimentResponse create(@RequestBody ExperimentRequest request) {
        return ExperimentResponse.from(experimentService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('chaos.view')")
    public List<ExperimentResponse> list() {
        return experimentService.findAll().stream()
                .map(ExperimentResponse::from)
                .toList();
    }

    @GetMapping("/{experimentId}")
    @PreAuthorize("hasAuthority('chaos.view')")
    public ExperimentResponse get(@PathVariable UUID experimentId) {
        return ExperimentResponse.from(experimentService.findById(experimentId));
    }

    @PutMapping("/{experimentId}")
    @PreAuthorize("hasAuthority('chaos.operate')")
    public ExperimentResponse update(@PathVariable UUID experimentId,
                                     @RequestBody ExperimentRequest request) {
        return ExperimentResponse.from(experimentService.update(experimentId, request));
    }

    @DeleteMapping("/{experimentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('chaos.operate')")
    public void delete(@PathVariable UUID experimentId) {
        experimentService.delete(experimentId);
    }

    @ExceptionHandler(ExperimentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
    }

    @ExceptionHandler(ExperimentValidationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ExperimentValidationResponse handleValidation(ExperimentValidationException exception) {
        return new ExperimentValidationResponse("Experiment validation failed.", exception.getErrors());
    }
}
