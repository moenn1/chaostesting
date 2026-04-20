package com.myg.controlplane.agents.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "chaos.auth.mode=dev"
})
class AgentCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Test
    void createsPollsAndStopsAnAssignedCommand() throws Exception {
        String agentId = registerAgent("agent-runtime-1");

        MvcResult createCommand = mockMvc.perform(post("/agent-commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "faultType": "Latency",
                                  "parameters": {
                                    "delayMs": "250"
                                  },
                                  "durationSeconds": 45,
                                  "targetScope": "checkout-service"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.faultType").value("latency"))
                .andReturn();

        String commandId = JsonFieldExtractor.read(createCommand.getResponse().getContentAsString(), "id");

        mockMvc.perform(post("/agent-commands/poll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commandId))
                .andExpect(jsonPath("$.deliveryCount").value(1))
                .andExpect(jsonPath("$.parameters.delayMs").value("250"))
                .andExpect(jsonPath("$.targetScope").value("checkout-service"));

        mockMvc.perform(post("/agent-commands/{commandId}/events", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "status": "RECEIVED",
                                  "message": "Received by the runtime"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        clock.advanceSeconds(5);

        mockMvc.perform(post("/agent-commands/{commandId}/events", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "status": "RUNNING",
                                  "message": "Injection active"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.startedAt").exists());

        mockMvc.perform(post("/agent-commands/{commandId}/stop", commandId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOP_REQUESTED"));

        mockMvc.perform(post("/agent-commands/poll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOP_REQUESTED"));

        clock.advanceSeconds(1);

        mockMvc.perform(post("/agent-commands/{commandId}/events", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "status": "STOPPED",
                                  "message": "Cleanup finished"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.finishedAt").exists());

        mockMvc.perform(get("/agent-commands/{commandId}", commandId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.latestMessage").value("Cleanup finished"));
    }

    @Test
    void duplicateRunningReportsAreIdempotentAndRegressionIsRejected() throws Exception {
        String agentId = registerAgent("agent-runtime-2");
        String commandId = createCommand(agentId);

        mockMvc.perform(post("/agent-commands/{commandId}/events", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "status": "RECEIVED"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        mockMvc.perform(post("/agent-commands/{commandId}/events", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "status": "RUNNING"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(post("/agent-commands/{commandId}/events", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "status": "RUNNING",
                                  "message": "Duplicate delivery while still running"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.latestMessage").value("Duplicate delivery while still running"));

        mockMvc.perform(post("/agent-commands/{commandId}/events", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "status": "COMPLETED"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post("/agent-commands/{commandId}/events", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "status": "RUNNING"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isConflict());
    }

    private String registerAgent(String name) throws Exception {
        MvcResult registration = mockMvc.perform(post("/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "hostname": "node-a.internal",
                                  "environment": "staging",
                                  "region": "eu-west-1",
                                  "supportedFaultCapabilities": ["latency", "http_error"]
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonFieldExtractor.read(registration.getResponse().getContentAsString(), "id");
    }

    private String createCommand(String agentId) throws Exception {
        MvcResult createCommand = mockMvc.perform(post("/agent-commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "faultType": "http_error",
                                  "parameters": {
                                    "statusCode": "503"
                                  },
                                  "durationSeconds": 20,
                                  "targetScope": "payments-service"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonFieldExtractor.read(createCommand.getResponse().getContentAsString(), "id");
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-04-20T16:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        Clock testClock(MutableClock mutableClock) {
            return mutableClock;
        }
    }

    static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zoneId;

        MutableClock(Instant instant, ZoneId zoneId) {
            this.instant = instant;
            this.zoneId = zoneId;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
