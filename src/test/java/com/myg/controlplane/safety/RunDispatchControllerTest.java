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
    void validatesNonProductionDispatchesWithoutApproval() throws Exception {
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
    void authorizesAndStopsRequestDropRunsWithTelemetryAndAuditTrail() throws Exception {
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
                .andExpect(jsonPath("$.faultType").value("request_drop"))
                .andExpect(jsonPath("$.dropPercentage").value(12))
                .andReturn();

        String runId = readField(dispatch.getResponse().getContentAsString(), "dispatchId");

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

        mockMvc.perform(as(get("/safety/audit-records").param("resourceId", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].action").value("RUN_ROLLBACK_VERIFIED"))
                .andExpect(jsonPath("$[0].metadata.dropPercentage").value(12))
                .andExpect(jsonPath("$[2].action").value("RUN_STARTED"))
                .andExpect(jsonPath("$[2].metadata.faultType").value("request_drop"));
    }

    @Test
    void requiresApprovalBeforeDispatchingIntoProductionLikeEnvironment() throws Exception {
        mockMvc.perform(as(post("/safety/dispatches"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "prod",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120,
                                  "latencyMilliseconds": 350,
                                  "trafficPercentage": 30,
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.decision").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.requiresApproval").value(true))
                .andExpect(jsonPath("$.violations[0].code").value("APPROVAL_REQUIRED"));
    }

    @Test
    void authorizesProductionDispatchWhenAnApprovalExists() throws Exception {
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

        mockMvc.perform(as(post("/safety/dispatches"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "prod",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 300,
                                  "latencyMilliseconds": 350,
                                  "trafficPercentage": 30,
                                  "approvalId": "%s",
                                  "requestedBy": "experiment-operator"
                                }
                                """.formatted(approvalId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.approvalId").value(approvalId))
                .andExpect(jsonPath("$.latencyMilliseconds").value(350))
                .andExpect(jsonPath("$.trafficPercentage").value(30))
                .andExpect(jsonPath("$.targetEnvironment").value("prod"));

        mockMvc.perform(as(get("/audit/events")
                        .param("action", "approval_created")
                        .param("resourceType", "approval")
                        .param("resourceId", approvalId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].actor").value("platform-admin"))
                .andExpect(jsonPath("$[0].metadata.targetEnvironment").value("prod"));

        mockMvc.perform(as(get("/audit/events")
                        .param("action", "run_started")
                        .param("resourceType", "run")
                        .param("actor", "experiment-operator"), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].metadata.approvalId").value(approvalId))
                .andExpect(jsonPath("$[0].metadata.targetSelector").value("checkout-service"));
    }

    @Test
    void stopEndpointRollsBackRunAndPersistsTelemetryAndAuditTrail() throws Exception {
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

        String runId = readField(dispatch.getResponse().getContentAsString(), "dispatchId");

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

        mockMvc.perform(as(get("/safety/audit-records").param("resourceId", runId), "viewer", "VIEWER"))
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
    void enablingKillSwitchStopsActiveRunsAndWritesAuditMetadata() throws Exception {
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

        String runId = readField(dispatch.getResponse().getContentAsString(), "dispatchId");
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
                .andExpect(jsonPath("$.lastEnableReason").value("customer-impact containment"))
                .andExpect(jsonPath("$.activeRunCount").value(0))
                .andExpect(jsonPath("$.stopRequestedRunCount").value(0))
                .andExpect(jsonPath("$.rolledBackRunCount").value(1))
                .andExpect(jsonPath("$.stopRequestsIssued").value(1));

        mockMvc.perform(as(get("/safety/runs").param("status", "rolled_back"), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(runId))
                .andExpect(jsonPath("$[0].status").value("ROLLED_BACK"))
                .andExpect(jsonPath("$[0].stopCommandIssuedBy").value("ops-oncall"))
                .andExpect(jsonPath("$[0].stopCommandReason").value("customer-impact containment"));

        mockMvc.perform(as(get("/safety/audit-records"), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].action").value("RUN_ROLLBACK_VERIFIED"))
                .andExpect(jsonPath("$[0].resourceId").value(runId))
                .andExpect(jsonPath("$[?(@.action=='RUN_STOP_REQUESTED' && @.resourceId=='%s')]".formatted(runId)).exists())
                .andExpect(jsonPath("$[?(@.action=='KILL_SWITCH_ENABLED' && @.resourceId=='global')]").exists())
                .andExpect(jsonPath("$[?(@.action=='RUN_STARTED' && @.resourceId=='%s')]".formatted(runId)).exists());
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

        mockMvc.perform(as(get("/safety/runs"), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(as(get("/audit/events")
                        .param("action", "run_start_rejected")
                        .param("actor", "ops-oncall")
                        .param("resourceType", "run_dispatch"), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].summary").value("Run start rejected by safety guardrails"))
                .andExpect(jsonPath("$[0].metadata.targetEnvironment").value("staging"))
                .andExpect(jsonPath("$[0].metadata.violations[0].code").value("KILL_SWITCH_ACTIVE"));
    }

    @Test
    void rejectsDispatchesThatExceedTheConfiguredMaximumDuration() throws Exception {
        mockMvc.perform(as(post("/safety/dispatches/validate"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 1200,
                                  "latencyMilliseconds": 350,
                                  "trafficPercentage": 30,
                                  "requestedBy": "experiment-operator"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECTED"))
                .andExpect(jsonPath("$.maxDurationSeconds").value(900))
                .andExpect(jsonPath("$.violations[0].code").value("MAX_DURATION_EXCEEDED"));
    }

    @Test
    void disablingKillSwitchWritesAuditMetadata() throws Exception {
        mockMvc.perform(as(post("/safety/kill-switch/enable"), "ops-oncall", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "region isolation"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(as(post("/safety/kill-switch/disable"), "ops-oncall", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "recovered"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.lastDisabledBy").value("ops-oncall"))
                .andExpect(jsonPath("$.lastDisableReason").value("recovered"));

        mockMvc.perform(as(get("/audit/events")
                        .param("action", "kill_switch_disabled")
                        .param("resourceType", "kill_switch"), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].actor").value("ops-oncall"))
                .andExpect(jsonPath("$[0].summary").value("recovered"));
    }

    @Test
    void operatorCanStopRunsAndAuditUsesAuthenticatedActor() throws Exception {
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

        String runId = readField(dispatch.getResponse().getContentAsString(), "dispatchId");

        mockMvc.perform(as(post("/safety/runs/{runId}/stop", runId), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "manual abort"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"))
                .andExpect(jsonPath("$.stopCommandIssuedBy").value("experiment-operator"))
                .andExpect(jsonPath("$.stopCommandReason").value("manual abort"));

        mockMvc.perform(as(get("/audit/events")
                        .param("action", "run_stop_requested")
                        .param("resourceType", "run")
                        .param("resourceId", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].actor").value("experiment-operator"))
                .andExpect(jsonPath("$[0].summary").value("Stop requested for checkout-service"));
    }

    @Test
    void viewerCannotMutateDispatchApprovalOrAdminRoutes() throws Exception {
        mockMvc.perform(as(post("/safety/dispatches")
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
                                """), "observer", "VIEWER"))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(post("/safety/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "prod",
                                  "reason": "nope"
                                }
                                """), "observer", "VIEWER"))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(post("/safety/kill-switch/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "nope"
                                }
                                """), "observer", "VIEWER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approverCanApproveButCannotDispatch() throws Exception {
        mockMvc.perform(as(post("/safety/approvals"), "platform-approver", "APPROVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "prod",
                                  "reason": "maintenance window"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvedBy").value("platform-approver"));

        mockMvc.perform(as(post("/safety/dispatches"), "platform-approver", "APPROVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120,
                                  "latencyMilliseconds": 350,
                                  "trafficPercentage": 30,
                                  "requestedBy": "platform-approver"
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
            return Mockito.mock(TaskScheduler.class);
        }
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
