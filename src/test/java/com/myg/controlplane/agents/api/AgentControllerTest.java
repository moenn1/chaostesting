package com.myg.controlplane.agents.api;

import static org.hamcrest.Matchers.hasSize;
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
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Test
    void registersAgentAndReturnsItById() throws Exception {
        MvcResult registration = mockMvc.perform(post("/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "agent-eu-1",
                                  "hostname": "node-a.internal",
                                  "environment": "staging",
                                  "region": "eu-west-1",
                                  "supportedFaultCapabilities": ["Latency", "Http_Error"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("agent-eu-1"))
                .andExpect(jsonPath("$.hostname").value("node-a.internal"))
                .andExpect(jsonPath("$.environment").value("staging"))
                .andExpect(jsonPath("$.region").value("eu-west-1"))
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.supportedFaultCapabilities[0]").value("http_error"))
                .andExpect(jsonPath("$.supportedFaultCapabilities[1]").value("latency"))
                .andReturn();

        String agentId = JsonFieldExtractor.read(registration.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/agents/{agentId}", agentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agentId))
                .andExpect(jsonPath("$.status").value("HEALTHY"));
    }

    @Test
    void heartbeatRevivesAStaleAgent() throws Exception {
        MvcResult registration = mockMvc.perform(post("/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "agent-us-1",
                                  "hostname": "node-b.internal",
                                  "environment": "prod",
                                  "region": "us-east-1",
                                  "supportedFaultCapabilities": ["latency"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String agentId = JsonFieldExtractor.read(registration.getResponse().getContentAsString(), "id");
        clock.advanceSeconds(31);

        mockMvc.perform(get("/agents").param("status", "stale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(agentId))
                .andExpect(jsonPath("$[0].status").value("STALE"));

        mockMvc.perform(post("/agents/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agentId))
                .andExpect(jsonPath("$.status").value("HEALTHY"));

        mockMvc.perform(get("/agents").param("status", "healthy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='%s')]".formatted(agentId)).exists());
    }

    @Test
    void filtersAgentsByEnvironmentRegionAndCapability() throws Exception {
        mockMvc.perform(post("/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "agent-prod-eu",
                                  "hostname": "node-c.internal",
                                  "environment": "prod",
                                  "region": "eu-central-1",
                                  "supportedFaultCapabilities": ["latency", "process_kill"]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "agent-dev-us",
                                  "hostname": "node-d.internal",
                                  "environment": "dev",
                                  "region": "us-east-1",
                                  "supportedFaultCapabilities": ["http_error"]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/agents")
                        .param("environment", "prod")
                        .param("region", "eu-central-1")
                        .param("capability", "process_kill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("agent-prod-eu"));
    }

    @Test
    void returnsNotFoundForUnknownHeartbeat() throws Exception {
        mockMvc.perform(post("/agents/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "2d704f14-9f09-4372-a0c2-96888ec270f1"
                                }
                                """))
                .andExpect(status().isNotFound());
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
