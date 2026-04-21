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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:testdb-safety;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "chaos.guardrails.environment-policy-mode=allowlist",
        "chaos.guardrails.controlled-environments[0]=dev",
        "chaos.guardrails.controlled-environments[1]=staging",
        "chaos.guardrails.controlled-environments[2]=prod",
        "chaos.guardrails.production-like-environments[0]=prod",
        "chaos.guardrails.max-duration=15m",
        "chaos.guardrails.approval-ttl=30m",
        "chaos.auth.mode=dev",
        "chaos.latency.max-latency=5s"
})
class RunDispatchControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEV_USER_HEADER = "X-Chaos-Dev-User";
    private static final String DEV_ROLES_HEADER = "X-Chaos-Dev-Roles";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Test
    void validatesNonProductionLatencyDispatchesWithoutApproval() throws Exception {
        mockMvc.perform(as(post("/safety/dispatches/validate"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120,
                                  "latencyMilliseconds": 350,
                                  "trafficPercentage": 30,
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.maxLatencyMilliseconds").value(5000))
                .andExpect(jsonPath("$.violations", hasSize(0)));
    }

    @Test
    void validatesScopedHttpErrorDispatches() throws Exception {
        mockMvc.perform(as(post("/safety/dispatches/validate"), "experiment-operator", "OPERATOR")
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
    void validatesProcessKillAndTimedServicePauseDispatches() throws Exception {
        mockMvc.perform(as(post("/safety/dispatches/validate"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-worker",
                                  "faultType": "process_kill",
                                  "requestedDurationSeconds": 120,
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.violations", hasSize(0)));

        mockMvc.perform(as(post("/safety/dispatches/validate"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "inventory-daemon",
                                  "faultType": "service_pause",
                                  "requestedDurationSeconds": 180,
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.violations", hasSize(0)));
    }

    @Test
    void validatesRandomLatencyDispatchesWithEitherJitterOrBounds() throws Exception {
        mockMvc.perform(as(post("/safety/dispatches/validate"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120,
                                  "latencyMilliseconds": 350,
                                  "latencyJitterMilliseconds": 40,
                                  "trafficPercentage": 30,
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.latencyJitterMilliseconds").value(40))
                .andExpect(jsonPath("$.violations", hasSize(0)));

        mockMvc.perform(as(post("/safety/dispatches/validate"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120,
                                  "latencyMinimumMilliseconds": 120,
                                  "latencyMaximumMilliseconds": 360,
                                  "trafficPercentage": 30,
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.latencyMinimumMilliseconds").value(120))
                .andExpect(jsonPath("$.latencyMaximumMilliseconds").value(360))
                .andExpect(jsonPath("$.violations", hasSize(0)));
    }

    @Test
    void authorizesProductionHttpErrorDispatchWhenApprovalExists() throws Exception {
        MvcResult approvalCreation = mockMvc.perform(as(post("/safety/approvals"), "platform-admin", "APPROVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "prod",
                                  "reason": "approved canary chaos window"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String approvalId = readField(approvalCreation.getResponse().getContentAsString(), "id");

        MvcResult dispatch = mockMvc.perform(as(post("/safety/dispatches"), "experiment-operator", "OPERATOR")
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

        mockMvc.perform(as(get("/safety/runs/{runId}", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.errorCode").value(500))
                .andExpect(jsonPath("$.trafficPercentage").value(15))
                .andExpect(jsonPath("$.routeFilters[0]").value("/checkout"))
                .andExpect(jsonPath("$.rollbackScheduledAt").exists());
    }

    @Test
    void authorizesAndStopsRequestDropRunsWithTelemetryAndAuditTrail() throws Exception {
        String runId = dispatchRequestDropRun();

        mockMvc.perform(as(get("/safety/runs/{runId}/telemetry", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].faultType").value("request_drop"))
                .andExpect(jsonPath("$[0].dropPercentage").value(12))
                .andExpect(jsonPath("$[0].rollbackVerified").value(false));

        clock.advanceSeconds(1);

        mockMvc.perform(as(post("/safety/runs/{runId}/stop", runId), "ops-oncall", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "customer-impact containment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"))
                .andExpect(jsonPath("$.faultType").value("request_drop"))
                .andExpect(jsonPath("$.dropPercentage").value(12))
                .andExpect(jsonPath("$.rollbackVerifiedAt").exists());

        mockMvc.perform(as(get("/safety/runs/{runId}/telemetry", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].phase").value("ROLLBACK"))
                .andExpect(jsonPath("$[0].faultType").value("request_drop"))
                .andExpect(jsonPath("$[0].dropPercentage").value(12))
                .andExpect(jsonPath("$[0].rollbackVerified").value(true));

        mockMvc.perform(as(get("/audit/events").param("resourceId", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].action").value("RUN_ROLLBACK_VERIFIED"))
                .andExpect(jsonPath("$[0].metadata.dropPercentage").value(12))
                .andExpect(jsonPath("$[2].action").value("RUN_STARTED"))
                .andExpect(jsonPath("$[2].metadata.faultType").value("request_drop"));
    }

    @Test
    void stopEndpointRollsBackLatencyRunAndPersistsTelemetryAndAuditTrail() throws Exception {
        String runId = dispatchLatencyRun();

        mockMvc.perform(as(get("/safety/runs/{runId}/telemetry", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].phase").value("INJECTION"))
                .andExpect(jsonPath("$[0].latencyMilliseconds").value(350))
                .andExpect(jsonPath("$[0].trafficPercentage").value(30))
                .andExpect(jsonPath("$[0].rollbackVerified").value(false));

        clock.advanceSeconds(1);

        mockMvc.perform(as(post("/safety/runs/{runId}/stop", runId), "ops-oncall", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "customer-impact containment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"))
                .andExpect(jsonPath("$.stopCommandIssuedBy").value("ops-oncall"))
                .andExpect(jsonPath("$.stopCommandReason").value("customer-impact containment"))
                .andExpect(jsonPath("$.rollbackVerifiedAt").exists());

        mockMvc.perform(as(get("/safety/runs/{runId}/telemetry", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].phase").value("ROLLBACK"))
                .andExpect(jsonPath("$[0].rollbackVerified").value(true))
                .andExpect(jsonPath("$[1].phase").value("INJECTION"));

        mockMvc.perform(as(get("/audit/events").param("resourceId", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].action").value("RUN_ROLLBACK_VERIFIED"))
                .andExpect(jsonPath("$[0].actor").value("ops-oncall"))
                .andExpect(jsonPath("$[1].action").value("RUN_STOP_REQUESTED"))
                .andExpect(jsonPath("$[1].actor").value("ops-oncall"))
                .andExpect(jsonPath("$[2].action").value("RUN_STARTED"))
                .andExpect(jsonPath("$[2].actor").value("experiment-operator"));
    }

    @Test
    void reportEndpointTracksFailureStateAndAudit() throws Exception {
        String runId = dispatchHttpErrorRun();
        clock.advanceSeconds(1);

        mockMvc.perform(as(post("/safety/runs/{runId}/reports", runId), "agent-eu-1", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "state": "FAILURE",
                                  "message": "Failed to attach scoped route filter."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILURE"))
                .andExpect(jsonPath("$.reportedBy").value("agent-eu-1"));

        mockMvc.perform(as(get("/safety/runs/{runId}", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(as(get("/safety/runs/{runId}/reports", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].state").value("FAILURE"))
                .andExpect(jsonPath("$[0].reportedBy").value("agent-eu-1"));

        mockMvc.perform(as(get("/audit/events").param("resourceId", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("RUN_EXECUTION_FAILED"))
                .andExpect(jsonPath("$[0].actor").value("agent-eu-1"));
    }

    @Test
    void processKillDispatchReportsRecoveryMessagingThroughRunAndTelemetry() throws Exception {
        MvcResult dispatch = mockMvc.perform(as(post("/safety/dispatches"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-worker",
                                  "faultType": "process_kill",
                                  "requestedDurationSeconds": 120,
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        String runId = readField(dispatch.getResponse().getContentAsString(), "dispatchId");

        mockMvc.perform(as(get("/safety/runs/{runId}", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.statusMessage").value("Process kill against checkout-worker activated."));

        mockMvc.perform(as(post("/safety/runs/{runId}/stop", runId), "ops-oncall", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "recovery validated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"))
                .andExpect(jsonPath("$.statusMessage").value("Process recovery verified after recovery validated."));

        mockMvc.perform(as(get("/safety/runs/{runId}/telemetry", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].message").value("Process recovery verified after recovery validated."))
                .andExpect(jsonPath("$[1].message").value("Process kill against checkout-worker activated."));
    }

    @Test
    void enablingKillSwitchStopsActiveRunsAndWritesAuditMetadata() throws Exception {
        String runId = dispatchLatencyRun();
        clock.advanceSeconds(1);

        mockMvc.perform(as(post("/safety/kill-switch/enable"), "ops-oncall", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "customer-impact containment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.lastEnabledBy").value("ops-oncall"))
                .andExpect(jsonPath("$.activeRunCount").value(0))
                .andExpect(jsonPath("$.stopRequestedRunCount").value(0))
                .andExpect(jsonPath("$.rolledBackRunCount").value(1))
                .andExpect(jsonPath("$.stopRequestsIssued").value(1));

        mockMvc.perform(as(get("/safety/runs").param("status", "rolled_back"), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(runId))
                .andExpect(jsonPath("$[0].status").value("ROLLED_BACK"))
                .andExpect(jsonPath("$[0].stopCommandIssuedBy").value("ops-oncall"));
    }

    @Test
    void blocksNewRunCreationWhileKillSwitchIsEnabled() throws Exception {
        mockMvc.perform(as(post("/safety/kill-switch/enable"), "ops-oncall", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "region isolation"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(as(post("/safety/dispatches"), "ops-oncall", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120,
                                  "latencyMilliseconds": 350,
                                  "trafficPercentage": 30,
                                  "requestedBy": "ops-oncall"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.decision").value("REJECTED"))
                .andExpect(jsonPath("$.violations[0].code").value("KILL_SWITCH_ACTIVE"));
    }

    @Test
    void rejectingSecondStopReturnsClearValidationResponse() throws Exception {
        String runId = dispatchLatencyRun();

        mockMvc.perform(as(post("/safety/runs/{runId}/stop", runId), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "manual abort"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(as(post("/safety/runs/{runId}/stop", runId), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "manual abort"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RUN_ALREADY_ROLLED_BACK"))
                .andExpect(jsonPath("$.message").value("Run has already been rolled back."))
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.currentStatus").value("ROLLED_BACK"))
                .andExpect(jsonPath("$.stoppableStatuses[0]").value("ACTIVE"));
    }

    @Test
    void rejectsUnsupportedManualDispatchFaultTypes() throws Exception {
        mockMvc.perform(as(post("/safety/dispatches/validate"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "inventory-daemon",
                                  "faultType": "consumer_pause",
                                  "requestedDurationSeconds": 180,
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECTED"))
                .andExpect(jsonPath("$.violations[0].code").value("UNSUPPORTED_FAULT_TYPE"));
    }

    @Test
    void viewerCannotMutateDispatchApprovalOrAdminRoutes() throws Exception {
        mockMvc.perform(as(post("/safety/dispatches"), "observer", "VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120,
                                  "latencyMilliseconds": 350,
                                  "trafficPercentage": 30,
                                  "requestedBy": "observer"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(post("/safety/approvals"), "observer", "VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "prod",
                                  "reason": "nope"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(post("/safety/kill-switch/enable"), "observer", "VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "nope"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void authEndpointReportsResolvedRolesAndPermissions() throws Exception {
        mockMvc.perform(as(get("/auth/me"), "platform-admin", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("platform-admin"))
                .andExpect(jsonPath("$.mode").value("DEV"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.permissions", hasSize(4)));
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
            Mockito.when(scheduler.scheduleAtFixedRate(Mockito.any(Runnable.class), Mockito.any(Instant.class), Mockito.any()))
                    .thenReturn(Mockito.mock(ScheduledFuture.class));
            return scheduler;
        }
    }

    private String dispatchLatencyRun() throws Exception {
        MvcResult dispatch = mockMvc.perform(as(post("/safety/dispatches"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120,
                                  "latencyMilliseconds": 350,
                                  "trafficPercentage": 30,
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();
        return readField(dispatch.getResponse().getContentAsString(), "dispatchId");
    }

    private String dispatchHttpErrorRun() throws Exception {
        MvcResult dispatch = mockMvc.perform(as(post("/safety/dispatches"), "experiment-operator", "OPERATOR")
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

    private String dispatchRequestDropRun() throws Exception {
        MvcResult dispatch = mockMvc.perform(as(post("/safety/dispatches"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "edge-gateway",
                                  "faultType": "request_drop",
                                  "requestedDurationSeconds": 120,
                                  "dropPercentage": 12,
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

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String username, String roles) {
        return builder
                .header(DEV_USER_HEADER, username)
                .header(DEV_ROLES_HEADER, roles);
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
