package com.myg.controlplane.agents.api;

import com.myg.controlplane.agents.domain.AgentStatus;
import com.myg.controlplane.agents.service.AgentNotFoundException;
import com.myg.controlplane.agents.service.AgentRegistryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/agents")
public class AgentController {

    private final AgentRegistryService agentRegistryService;

    public AgentController(AgentRegistryService agentRegistryService) {
        this.agentRegistryService = agentRegistryService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentResponse register(@Valid @RequestBody RegisterAgentRequest request) {
        return AgentResponse.from(agentRegistryService.register(
                request.name(),
                request.hostname(),
                request.environment(),
                request.region(),
                request.supportedFaultCapabilities()
        ));
    }

    @PostMapping("/heartbeat")
    public AgentResponse heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        return AgentResponse.from(agentRegistryService.heartbeat(request.agentId()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('chaos.view')")
    public List<AgentResponse> listAgents(@RequestParam Optional<String> environment,
                                          @RequestParam Optional<String> region,
                                          @RequestParam Optional<String> status,
                                          @RequestParam Optional<String> capability) {
        Optional<AgentStatus> parsedStatus = status.map(this::parseStatus);
        return agentRegistryService.findAll(environment, region, parsedStatus, capability).stream()
                .map(AgentResponse::from)
                .toList();
    }

    @GetMapping("/{agentId}")
    @PreAuthorize("hasAuthority('chaos.view')")
    public AgentResponse getAgent(@PathVariable UUID agentId) {
        return agentRegistryService.findById(agentId)
                .map(AgentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
    }

    @ExceptionHandler(AgentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleAgentNotFound() {
    }

    private AgentStatus parseStatus(String rawStatus) {
        try {
            return AgentStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status filter: " + rawStatus);
        }
    }
}
