package com.myg.controlplane.safety;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
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

    @Autowired
    private MutableClock clock;

    @Test
    void validatesScopedHttpErrorDispatches() throws Exception {
        mockMvc.perform(post("/safety/dispatches/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "http_error",
                                  "requestedDurationSeconds": 120,
                                  "errorCode": 503,
                                  "trafficPercentage": 30,
                                  "routeFilters": ["/checkout", "/payments/authorize"],
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.violations", hasSize(0)));
    }

    @Test
    void rejectsHttpErrorDispatchesMissingRequiredConfig() throws Exception {
        mockMvc.perform(post("/safety/dispatches/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "http_error",
                                  "requestedDurationSeconds": 120,
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECTED"))
                .andExpect(jsonPath("$.violations", hasSize(2)))
                .andExpect(jsonPath("$.violations[0].code").value("HTTP_ERROR_CODE_REQUIRED"))
                .andExpect(jsonPath("$.violations[1].code").value("TRAFFIC_PERCENTAGE_REQUIRED"));
    }

    @Test
    void authorizesProductionHttpErrorDispatchWhenApprovalExists() throws Exception {
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

        MvcResult dispatch = mockMvc.perform(post("/safety/dispatches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "prod",
                                  "targetSelector": "checkout-service",
                                  "faultType": "http_error",
                                  "requestedDurationSeconds": 300,
                                  "errorCode": 500,
                                  "trafficPercentage": 15,
                                  "routeFilters": ["/checkout"],
                                  "approvalId": "%s",
                                  "requestedBy": "experiment-operator"
                                }
                                """.formatted(approvalId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.approvalId").value(approvalId))
                .andExpect(jsonPath("$.errorCode").value(500))
                .andExpect(jsonPath("$.trafficPercentage").value(15))
                .andExpect(jsonPath("$.routeFilters[0]").value("/checkout"))
                .andReturn();

        String runId = readField(dispatch.getResponse().getContentAsString(), "dispatchId");

        mockMvc.perform(get("/safety/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.errorCode").value(500))
                .andExpect(jsonPath("$.trafficPercentage").value(15))
                .andExpect(jsonPath("$.routeFilters[0]").value("/checkout"))
                .andExpect(jsonPath("$.rollbackScheduledAt").exists());
    }

    @Test
    void reportEndpointTracksSuccessAndFailureStates() throws Exception {
        String runId = dispatchHttpErrorRun();

        mockMvc.perform(post("/safety/runs/{runId}/reports", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "state": "SUCCESS",
                                  "reportedBy": "agent-eu-1",
                                  "message": "Injected 503 responses for 30% of scoped traffic."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCESS"))
                .andExpect(jsonPath("$.reportedBy").value("agent-eu-1"));

        clock.advanceSeconds(1);

        mockMvc.perform(post("/safety/runs/{runId}/reports", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "state": "FAILURE",
                                  "reportedBy": "agent-eu-1",
                                  "message": "Failed to attach scoped route filter."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILURE"))
                .andExpect(jsonPath("$.message").value("Failed to attach scoped route filter."));

        mockMvc.perform(get("/safety/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(get("/safety/runs/{runId}/reports", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].state").value("FAILURE"))
                .andExpect(jsonPath("$[1].state").value("SUCCESS"));

        mockMvc.perform(get("/audit/events").param("resourceId", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("RUN_EXECUTION_FAILED"))
                .andExpect(jsonPath("$[0].actor").value("agent-eu-1"));
    }

    @Test
    void manualStopRollsBackRunAndPersistsRollbackReport() throws Exception {
        String runId = dispatchHttpErrorRun();
        clock.advanceSeconds(1);

        mockMvc.perform(post("/safety/runs/{runId}/stop", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operator": "ops-oncall",
                                  "reason": "customer-impact containment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"))
                .andExpect(jsonPath("$.stopCommandIssuedBy").value("ops-oncall"))
                .andExpect(jsonPath("$.stopCommandReason").value("customer-impact containment"))
                .andExpect(jsonPath("$.rollbackVerifiedAt").exists());

        mockMvc.perform(get("/safety/runs/{runId}/reports", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].state").value("ROLLBACK"))
                .andExpect(jsonPath("$[0].reportedBy").value("ops-oncall"));

        mockMvc.perform(get("/audit/events").param("resourceId", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].action").value("RUN_ROLLBACK_VERIFIED"))
                .andExpect(jsonPath("$[1].action").value("RUN_STOP_REQUESTED"))
                .andExpect(jsonPath("$[2].action").value("RUN_STARTED"));
    }

    @Test
    void enablingKillSwitchRollsBackActiveHttpErrorRuns() throws Exception {
        String runId = dispatchHttpErrorRun();
        clock.advanceSeconds(1);

        mockMvc.perform(post("/safety/kill-switch/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operator": "ops-oncall",
                                  "reason": "customer-impact containment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.activeRunCount").value(0))
                .andExpect(jsonPath("$.stopRequestedRunCount").value(0))
                .andExpect(jsonPath("$.rolledBackRunCount").value(1))
                .andExpect(jsonPath("$.stopRequestsIssued").value(1));

        mockMvc.perform(get("/safety/runs").param("status", "rolled_back"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(runId))
                .andExpect(jsonPath("$[0].status").value("ROLLED_BACK"));
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

        @Bean
        @Primary
        TaskScheduler testTaskScheduler() {
            TaskScheduler scheduler = Mockito.mock(TaskScheduler.class);
            Mockito.when(scheduler.schedule(Mockito.any(Runnable.class), Mockito.any(Instant.class)))
                    .thenReturn(Mockito.mock(ScheduledFuture.class));
            return scheduler;
        }
    }

    private String dispatchHttpErrorRun() throws Exception {
        MvcResult dispatch = mockMvc.perform(post("/safety/dispatches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "http_error",
                                  "requestedDurationSeconds": 120,
                                  "errorCode": 503,
                                  "trafficPercentage": 30,
                                  "routeFilters": ["/checkout"],
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();
        return readField(dispatch.getResponse().getContentAsString(), "dispatchId");
    }

    private static String readField(String json, String fieldName) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        return root.get(fieldName).asText();
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
