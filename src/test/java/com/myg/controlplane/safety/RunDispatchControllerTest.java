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
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
        "chaos.guardrails.approval-ttl=30m",
        "chaos.auth.mode=dev"
})
class RunDispatchControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEV_USER_HEADER = "X-Chaos-Dev-User";
    private static final String DEV_ROLES_HEADER = "X-Chaos-Dev-Roles";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChaosRunJpaRepository chaosRunJpaRepository;

    @Autowired
    private RunAssignmentJpaRepository runAssignmentJpaRepository;

    @Test
    void validatesNonProductionDispatchesWithoutApproval() throws Exception {
        mockMvc.perform(as(post("/safety/dispatches/validate"), "experiment-operator", "OPERATOR")
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
        mockMvc.perform(as(post("/safety/dispatches"), "experiment-operator", "OPERATOR")
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
                                  "approvalId": "%s"
                                }
                                """.formatted(approvalId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.approvalId").value(approvalId))
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
    void enablingKillSwitchStopsActiveRunsAndWritesAuditMetadata() throws Exception {
        registerAgent("staging-agent-01", "staging", "us-phoenix-1", "latency");

        MvcResult dispatch = mockMvc.perform(as(post("/safety/dispatches"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        String runId = readField(dispatch.getResponse().getContentAsString(), "dispatchId");

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
                .andExpect(jsonPath("$.stoppedRunCount").value(1));

        mockMvc.perform(as(get("/safety/runs").param("status", "stopped"), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(runId))
                .andExpect(jsonPath("$[0].status").value("STOPPED"))
                .andExpect(jsonPath("$[0].endedAt").value("2026-04-20T16:00:00Z"))
                .andExpect(jsonPath("$[0].stopCommandIssuedBy").value("ops-oncall"))
                .andExpect(jsonPath("$[0].stopCommandReason").value("customer-impact containment"))
                .andExpect(jsonPath("$[0].assignmentCount").value(1))
                .andExpect(jsonPath("$[0].activeAssignmentCount").value(0))
                .andExpect(jsonPath("$[0].stoppedAssignmentCount").value(1));

        mockMvc.perform(as(get("/audit/events")
                        .param("resourceType", "run")
                        .param("resourceId", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].action").value("RUN_STOP_REQUESTED"))
                .andExpect(jsonPath("$[0].resourceId").value(runId))
                .andExpect(jsonPath("$[0].actor").value("ops-oncall"))
                .andExpect(jsonPath("$[0].summary").value("customer-impact containment"))
                .andExpect(jsonPath("$[0].metadata.finalStatus").value("STOPPED"))
                .andExpect(jsonPath("$[1].action").value("RUN_STARTED"))
                .andExpect(jsonPath("$[1].actor").value("experiment-operator"))
                .andExpect(jsonPath("$[1].metadata.targetSelector").value("checkout-service"));

        mockMvc.perform(as(get("/safety/audit-records")
                        .param("action", "kill_switch_enabled")
                        .param("resourceType", "kill_switch"), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].actor").value("ops-oncall"))
                .andExpect(jsonPath("$[0].summary").value("customer-impact containment"));
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
                                  "requestedDurationSeconds": 120
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
                                  "requestedDurationSeconds": 1200
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
        registerAgent("staging-agent-02", "staging", "us-phoenix-1", "latency");

        MvcResult dispatch = mockMvc.perform(as(post("/safety/dispatches"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120
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
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.endedAt").value("2026-04-20T16:00:00Z"))
                .andExpect(jsonPath("$.stopCommandIssuedBy").value("experiment-operator"))
                .andExpect(jsonPath("$.stopCommandReason").value("manual abort"))
                .andExpect(jsonPath("$.assignmentCount").value(1))
                .andExpect(jsonPath("$.activeAssignmentCount").value(0))
                .andExpect(jsonPath("$.stoppedAssignmentCount").value(1));

        RunAssignment persistedAssignment = runAssignmentJpaRepository.findAllByRunId(UUID.fromString(runId)).get(0).toDomain();
        org.assertj.core.api.Assertions.assertThat(persistedAssignment.status()).isEqualTo(RunAssignmentStatus.STOPPED);
        org.assertj.core.api.Assertions.assertThat(persistedAssignment.stopRequestedAt()).isEqualTo(Instant.parse("2026-04-20T16:00:00Z"));
        org.assertj.core.api.Assertions.assertThat(persistedAssignment.endedAt()).isEqualTo(Instant.parse("2026-04-20T16:00:00Z"));

        mockMvc.perform(as(get("/audit/events")
                        .param("action", "run_stop_requested")
                        .param("resourceType", "run")
                        .param("resourceId", runId), "viewer", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].actor").value("experiment-operator"))
                .andExpect(jsonPath("$[0].summary").value("manual abort"))
                .andExpect(jsonPath("$[0].metadata.finalStatus").value("STOPPED"));
    }

    @Test
    void rejectingSecondStopReturnsClearValidationResponse() throws Exception {
        MvcResult dispatch = mockMvc.perform(as(post("/safety/dispatches"), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetEnvironment": "staging",
                                  "targetSelector": "checkout-service",
                                  "faultType": "latency",
                                  "requestedDurationSeconds": 120
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
                .andExpect(status().isOk());

        mockMvc.perform(as(post("/safety/runs/{runId}/stop", runId), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "manual abort"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RUN_ALREADY_STOPPED"))
                .andExpect(jsonPath("$.message").value("Run has already been stopped."))
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.currentStatus").value("STOPPED"))
                .andExpect(jsonPath("$.stoppableStatuses[0]").value("ACTIVE"));
    }

    @Test
    void completedRunsCannotBeStoppedAgain() throws Exception {
        UUID runId = UUID.randomUUID();
        chaosRunJpaRepository.save(new ChaosRunEntity(
                runId,
                "staging",
                "checkout-service",
                "latency",
                120,
                null,
                ChaosRunStatus.COMPLETED,
                Instant.parse("2026-04-20T15:50:00Z"),
                Instant.parse("2026-04-20T15:55:00Z"),
                null,
                null,
                null
        ));

        mockMvc.perform(as(post("/safety/runs/{runId}/stop", runId), "experiment-operator", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "manual abort"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RUN_ALREADY_COMPLETED"))
                .andExpect(jsonPath("$.message").value("Completed runs cannot be stopped again."))
                .andExpect(jsonPath("$.currentStatus").value("COMPLETED"));
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
                                  "requestedDurationSeconds": 120
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
                                  "requestedDurationSeconds": 120
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
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-04-20T16:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static String readField(String json, String fieldName) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        return root.get(fieldName).asText();
    }

    private void registerAgent(String name, String environment, String region, String... capabilities) throws Exception {
        String capabilitiesJson = Arrays.stream(capabilities)
                .map(capability -> "\"" + capability + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(post("/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "hostname": "%s.internal",
                                  "environment": "%s",
                                  "region": "%s",
                                  "supportedFaultCapabilities": [%s]
                                }
                                """.formatted(name, name, environment, region, capabilitiesJson)))
                .andExpect(status().isCreated());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String username, String roles) {
        return builder
                .header(DEV_USER_HEADER, username)
                .header(DEV_ROLES_HEADER, roles);
    }
}
