package com.myg.controlplane.agents.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AgentIdentityStore {

    private final AgentRuntimeProperties properties;
    private final ObjectMapper objectMapper;

    public AgentIdentityStore(AgentRuntimeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<PersistedAgentIdentity> load() {
        if (!Files.exists(properties.getRegistrationFile())) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(
                    properties.getRegistrationFile().toFile(),
                    PersistedAgentIdentity.class
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read persisted agent identity", exception);
        }
    }

    public void save(PersistedAgentIdentity identity) {
        try {
            if (properties.getRegistrationFile().getParent() != null) {
                Files.createDirectories(properties.getRegistrationFile().getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(properties.getRegistrationFile().toFile(), identity);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist agent identity", exception);
        }
    }
}
