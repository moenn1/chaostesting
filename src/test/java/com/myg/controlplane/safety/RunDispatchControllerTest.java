package com.myg.controlplane.safety;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
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
        "spring.datasource.url=jdbc:h2:mem:testdb-safety;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "chaos.guardrails.environment-policy-mode=allowlist",
        "chaos.guardrails.controlled-environments[0]=dev",
        "chaos.guardrails.controlled-environments[1]=staging",
        "chaos.guardrails.controlled-environments[2]=prod",
        "chaos.guardrails.production-like-environments[0]=prod",
        "chaos.guardrails.max-duration=15m",
        "chaos.guardrails.approval-ttl=30m"
})
class RunDispatchControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validatesNonProductionDispatchesWithoutApproval() throws Exception {
        mockMvc.perform(post("/safety/dispatches/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.violations", hasSize(0)));
    }

    @Test
    void requiresApprovalBeforeDispatchingIntoProductionLikeEnvironment() throws Exception {
        mockMvc.perform(post("/safety/dispatches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "prod",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.decision").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.requiresApproval").value(true))
                .andExpect(jsonPath("$.violations[0].code").value("APPROVAL_REQUIRED"));
    }

    @Test
    void authorizesProductionDispatchWhenAnApprovalExists() throws Exception {
        MvcResult approvalCreation = mockMvc.perform(post("/safety/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "prod",
                                  "approvedBy": "platform-admin",
                                  "reason": "approved canary chaos window"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String approvalId = readField(approvalCreation.getResponse().getContentAsString(), "id");

        mockMvc.perform(post("/safety/dispatches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "prod",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 300,
                                  "approvalId": "%s"
                                }
                                """.formatted(approvalId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.approvalId").value(approvalId))
                .andExpect(jsonPath("$.targetEnvironment").value("prod"));
    }

    @Test
    void rejectsDispatchesThatExceedTheConfiguredMaximumDuration() throws Exception {
        mockMvc.perform(post("/safety/dispatches/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 1200
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECTED"))
                .andExpect(jsonPath("$.maxDurationSeconds").value(900))
                .andExpect(jsonPath("$.violations[0].code").value("MAX_DURATION_EXCEEDED"));
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-04-20T16:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static String readField(String json, String fieldName) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        return root.get(fieldName).asText();
    }
}
