package com.myg.controlplane.agents.api;

import com.myg.controlplane.agents.service.AgentCommandConflictException;
import com.myg.controlplane.agents.service.AgentCommandNotFoundException;
import com.myg.controlplane.agents.service.AgentCommandService;
import com.myg.controlplane.agents.service.AgentNotFoundException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent-commands")
public class AgentCommandController {

    private final AgentCommandService agentCommandService;

    public AgentCommandController(AgentCommandService agentCommandService) {
        this.agentCommandService = agentCommandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentCommandResponse create(@Valid @RequestBody CreateAgentCommandRequest request) {
        return AgentCommandResponse.from(agentCommandService.assign(
                request.agentId(),
                request.faultType(),
                request.parameters(),
                request.durationSeconds(),
                request.targetScope()
        ));
    }

    @PostMapping("/poll")
    public ResponseEntity<AgentCommandResponse> poll(@Valid @RequestBody PollAgentCommandRequest request) {
        return agentCommandService.poll(request.agentId())
                .map(AgentCommandResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{commandId}")
    public AgentCommandResponse getCommand(@PathVariable UUID commandId) {
        return AgentCommandResponse.from(agentCommandService.findById(commandId));
    }

    @PostMapping("/{commandId}/events")
    public AgentCommandResponse reportExecution(@PathVariable UUID commandId,
                                                @Valid @RequestBody AgentCommandExecutionEventRequest request) {
        return AgentCommandResponse.from(agentCommandService.reportExecution(
                request.agentId(),
                commandId,
                request.status(),
                request.message()
        ));
    }

    @PostMapping("/{commandId}/stop")
    public AgentCommandResponse requestStop(@PathVariable UUID commandId) {
        return AgentCommandResponse.from(agentCommandService.requestStop(commandId));
    }

    @ExceptionHandler({AgentNotFoundException.class, AgentCommandNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
    }

    @ExceptionHandler(AgentCommandConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleConflict() {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleBadRequest() {
    }
}
