package com.myg.controlplane.agents.runtime;

import com.myg.controlplane.agents.api.AgentCommandExecutionEventRequest;
import com.myg.controlplane.agents.api.AgentCommandResponse;
import com.myg.controlplane.agents.api.AgentResponse;
import com.myg.controlplane.agents.api.HeartbeatRequest;
import com.myg.controlplane.agents.api.PollAgentCommandRequest;
import com.myg.controlplane.agents.api.RegisterAgentRequest;
import com.myg.controlplane.agents.domain.AgentCommandStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpAgentControlPlaneClient implements AgentControlPlaneClient {

    private final AgentRuntimeProperties properties;
    private final RestClient restClient;

    public HttpAgentControlPlaneClient(AgentRuntimeProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.getControlPlaneBaseUrl().toString())
                .build();
    }

    @Override
    public AgentResponse register() {
        return restClient.post()
                .uri("/agents/register")
                .body(new RegisterAgentRequest(
                        properties.getName(),
                        properties.getHostname(),
                        properties.getEnvironment(),
                        properties.getRegion(),
                        properties.getSupportedFaultCapabilities()
                ))
                .retrieve()
                .body(AgentResponse.class);
    }

    @Override
    public AgentResponse heartbeat(UUID agentId) {
        try {
            return restClient.post()
                    .uri("/agents/heartbeat")
                    .body(new HeartbeatRequest(agentId))
                    .retrieve()
                    .body(AgentResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new AgentIdentityRejectedException(agentId, exception);
            }
            throw exception;
        }
    }

    @Override
    public Optional<AgentCommandResponse> pollNextCommand(UUID agentId) {
        try {
            ResponseEntity<AgentCommandResponse> response = restClient.post()
                    .uri("/agent-commands/poll")
                    .body(new PollAgentCommandRequest(agentId))
                    .retrieve()
                    .toEntity(AgentCommandResponse.class);
            if (response.getStatusCode().value() == HttpStatus.NO_CONTENT.value() || response.getBody() == null) {
                return Optional.empty();
            }
            return Optional.of(response.getBody());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new AgentIdentityRejectedException(agentId, exception);
            }
            throw exception;
        }
    }

    @Override
    public AgentCommandResponse reportCommandState(UUID agentId,
                                                   UUID commandId,
                                                   AgentCommandStatus status,
                                                   String message) {
        return restClient.post()
                .uri("/agent-commands/{commandId}/events", commandId)
                .body(new AgentCommandExecutionEventRequest(agentId, status, message))
                .retrieve()
                .body(AgentCommandResponse.class);
    }
}
