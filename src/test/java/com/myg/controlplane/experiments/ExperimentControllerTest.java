package com.myg.controlplane.experiments;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myg.controlplane.safety.ChaosRunJpaRepository;
import com.myg.controlplane.safety.RunLifecycleEventJpaRepository;
import com.myg.controlplane.safety.RunLifecycleEventType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:testdb-experiments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "chaos.guardrails.max-duration=15m",
        "chaos.auth.mode=dev"
})
class ExperimentControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEV_USER_HEADER = "X-Chaos-Dev-User";
    private static final String DEV_ROLES_HEADER = "X-Chaos-Dev-Roles";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChaosRunJpaRepository chaosRunJpaRepository;

    @Autowired
    private RunLifecycleEventJpaRepository runLifecycleEventJpaRepository;

    @Test
    void supportsExperimentCrudWithStructuredPayloads() throws Exception {
        MvcResult created = mockMvc.perform(as(post("/api/experiments"), "operator-demo", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(latencyExperimentPayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Checkout latency envelope"))
                .andExpect(jsonPath("$.targetSelector.service").value("checkout-api"))
                .andExpect(jsonPath("$.targetSelector.labels.lane").value("canary"))
                .andExpect(jsonPath("$.faultConfig.type").value("latency"))
                .andExpect(jsonPath("$.faultConfig.parameters.latencyMs").value(350))
                .andExpect(jsonPath("$.safetyRules.abortConditions[0]").value("Abort if 5xx exceeds 2.5% for 90 seconds"))
                .andExpect(jsonPath("$.environmentMetadata.environment").value("staging"))
                .andReturn();

        String experimentId = readField(created.getResponse().getContentAsString(), "id");

        mockMvc.perform(as(get("/api/experiments"), "viewer-demo", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(experimentId))
                .andExpect(jsonPath("$[0].targetSelector.namespace").value("payments"))
                .andExpect(jsonPath("$[0].environmentMetadata.team").value("payments"));

        mockMvc.perform(as(get("/api/experiments/{experimentId}", experimentId), "viewer-demo", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(experimentId))
                .andExpect(jsonPath("$.faultConfig.durationSeconds").value(480));

        mockMvc.perform(as(put("/api/experiments/{experimentId}", experimentId), "operator-demo", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedLatencyExperimentPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Inject 500ms latency into a guarded checkout canary lane."))
                .andExpect(jsonPath("$.faultConfig.durationSeconds").value(600))
                .andExpect(jsonPath("$.faultConfig.parameters.percentage").value(20))
                .andExpect(jsonPath("$.faultConfig.parameters.jitterMs").value(50))
                .andExpect(jsonPath("$.safetyRules.maxAffectedTargets").value(1));

        mockMvc.perform(as(delete("/api/experiments/{experimentId}", experimentId), "operator-demo", "OPERATOR"))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(get("/api/experiments/{experimentId}", experimentId), "viewer-demo", "VIEWER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsIncompleteSelectorsAndInvalidFaultValuesWithActionableErrors() throws Exception {
        mockMvc.perform(as(post("/api/experiments"), "operator-demo", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Broken experiment",
                                  "description": "Missing selector scope and invalid latency values.",
                                  "targetSelector": {},
                                  "faultConfig": {
                                    "type": "latency",
                                    "durationSeconds": 1200,
                                    "parameters": {
                                      "latencyMs": 0,
                                      "percentage": 140
                                    }
                                  },
                                  "safetyRules": {
                                    "abortConditions": ["Abort if errors spike"],
                                    "maxAffectedTargets": 1,
                                    "approvalRequired": false,
                                    "rollbackMode": "automatic"
                                  },
                                  "environmentMetadata": {
                                    "environment": "staging"
                                  }
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Experiment validation failed."))
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[0].field").value("targetSelector"))
                .andExpect(jsonPath("$.errors[1].field").value("faultConfig.durationSeconds"))
                .andExpect(jsonPath("$.errors[?(@.field=='faultConfig.parameters.latencyMs')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='faultConfig.parameters.percentage')]").exists());
    }

    @Test
    void supportsRequestDropAndBoundedLatencyExperimentShapes() throws Exception {
        mockMvc.perform(as(post("/api/experiments"), "operator-demo", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Gateway random latency envelope",
                                  "description": "Apply bounded random latency across the gateway canary path.",
                                  "targetSelector": {
                                    "service": "edge-gateway",
                                    "namespace": "ingress"
                                  },
                                  "faultConfig": {
                                    "type": "latency",
                                    "durationSeconds": 300,
                                    "parameters": {
                                      "minLatencyMs": 120,
                                      "maxLatencyMs": 360,
                                      "percentage": 25
                                    }
                                  },
                                  "safetyRules": {
                                    "abortConditions": ["Abort if p95 exceeds 450ms"],
                                    "maxAffectedTargets": 1,
                                    "approvalRequired": false,
                                    "rollbackMode": "automatic"
                                  },
                                  "environmentMetadata": {
                                    "environment": "staging"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.faultConfig.parameters.minLatencyMs").value(120))
                .andExpect(jsonPath("$.faultConfig.parameters.maxLatencyMs").value(360));

        mockMvc.perform(as(post("/api/experiments"), "operator-demo", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Gateway request drop rehearsal",
                                  "description": "Drop a slice of ingress traffic without forcing an HTTP code.",
                                  "targetSelector": {
                                    "service": "edge-gateway",
                                    "namespace": "ingress"
                                  },
                                  "faultConfig": {
                                    "type": "request_drop",
                                    "durationSeconds": 180,
                                    "parameters": {
                                      "percentage": 12
                                    }
                                  },
                                  "safetyRules": {
                                    "abortConditions": ["Abort if checkout availability drops below 99.5%"],
                                    "maxAffectedTargets": 1,
                                    "approvalRequired": false,
                                    "rollbackMode": "automatic"
                                  },
                                  "environmentMetadata": {
                                    "environment": "staging"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.faultConfig.type").value("request_drop"))
                .andExpect(jsonPath("$.faultConfig.parameters.percentage").value(12));
    }

    @Test
    void viewerCanReadButCannotMutateExperiments() throws Exception {
        mockMvc.perform(as(post("/api/experiments"), "operator-demo", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(latencyExperimentPayload()))
                .andExpect(status().isCreated());

        mockMvc.perform(as(get("/api/experiments"), "viewer-demo", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(as(post("/api/experiments"), "viewer-demo", "VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(latencyExperimentPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    void startsManualRunWithPersistedTargetSnapshotAndLifecycleEvent() throws Exception {
        mockMvc.perform(post("/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "agent-phx-1",
                                  "hostname": "phx-node-1.internal",
                                  "environment": "staging",
                                  "region": "us-phoenix-1",
                                  "supportedFaultCapabilities": ["latency", "http_error"]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "agent-ashburn-1",
                                  "hostname": "iad-node-1.internal",
                                  "environment": "staging",
                                  "region": "us-ashburn-1",
                                  "supportedFaultCapabilities": ["latency"]
                                }
                                """))
                .andExpect(status().isCreated());

        String experimentId = createExperiment();

        MvcResult runStart = mockMvc.perform(as(post("/api/experiments/{experimentId}/runs", experimentId),
                        "operator-demo", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.experimentId").value(experimentId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.startedAt").value("2026-04-20T18:00:00Z"))
                .andExpect(jsonPath("$.targetEnvironment").value("staging"))
                .andExpect(jsonPath("$.targetSelector").value(containsString("service=checkout-api")))
                .andExpect(jsonPath("$.targetSnapshot.services", hasSize(1)))
                .andExpect(jsonPath("$.targetSnapshot.services[0]").value("checkout-api"))
                .andExpect(jsonPath("$.targetSnapshot.selector.labels.lane").value("canary"))
                .andExpect(jsonPath("$.targetSnapshot.environment.region").value("us-phoenix-1"))
                .andExpect(jsonPath("$.targetSnapshot.assignedAgents", hasSize(1)))
                .andExpect(jsonPath("$.targetSnapshot.assignedAgents[0].name").value("agent-phx-1"))
                .andExpect(jsonPath("$.targetSnapshot.assignedAgents[0].supportedFaultCapabilities[0]").value("http_error"))
                .andExpect(jsonPath("$.targetSnapshot.assignedAgents[0].supportedFaultCapabilities[1]").value("latency"))
                .andReturn();

        String runId = readField(runStart.getResponse().getContentAsString(), "id");

        List<com.myg.controlplane.safety.ChaosRun> runs = chaosRunJpaRepository.findAll().stream()
                .map(entity -> entity.toDomain(objectMapper))
                .toList();
        org.assertj.core.api.Assertions.assertThat(runs).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(runs.get(0).id()).hasToString(runId);
        org.assertj.core.api.Assertions.assertThat(runs.get(0).targetSnapshot()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(runs.get(0).targetSnapshot().assignedAgents()).hasSize(1);

        List<com.myg.controlplane.safety.RunLifecycleEvent> lifecycleEvents = runLifecycleEventJpaRepository.findAll().stream()
                .map(entity -> entity.toDomain(objectMapper))
                .toList();
        org.assertj.core.api.Assertions.assertThat(lifecycleEvents).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(lifecycleEvents.get(0).runId()).hasToString(runId);
        org.assertj.core.api.Assertions.assertThat(lifecycleEvents.get(0).eventType()).isEqualTo(RunLifecycleEventType.RUN_STARTED);
        org.assertj.core.api.Assertions.assertThat(lifecycleEvents.get(0).details()).containsEntry("experimentId", experimentId);
    }

    @Test
    void duplicateManualRunRequestsReturnTheExistingActiveRunWithoutDuplicatingEvents() throws Exception {
        mockMvc.perform(post("/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "agent-phx-1",
                                  "hostname": "phx-node-1.internal",
                                  "environment": "staging",
                                  "region": "us-phoenix-1",
                                  "supportedFaultCapabilities": ["latency"]
                                }
                                """))
                .andExpect(status().isCreated());

        String experimentId = createExperiment();

        MvcResult firstStart = mockMvc.perform(as(post("/api/experiments/{experimentId}/runs", experimentId),
                        "operator-demo", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        String runId = readField(firstStart.getResponse().getContentAsString(), "id");

        mockMvc.perform(as(post("/api/experiments/{experimentId}/runs", experimentId),
                        "operator-demo", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.experimentId").value(experimentId));

        org.assertj.core.api.Assertions.assertThat(chaosRunJpaRepository.findAll()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(runLifecycleEventJpaRepository.findAll()).hasSize(1);
    }

    @Test
    void rejectsManualRunWhenNoHealthyAgentsMatchTheTargetSnapshot() throws Exception {
        String experimentId = createExperiment();

        mockMvc.perform(as(post("/api/experiments/{experimentId}/runs", experimentId),
                        "operator-demo", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("No healthy registered agents matched environment 'staging'")));
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-04-20T18:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static String readField(String json, String fieldName) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        return root.get(fieldName).asText();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder,
                                                    String username,
                                                    String roles) {
        return builder
                .header(DEV_USER_HEADER, username)
                .header(DEV_ROLES_HEADER, roles);
    }

    private String createExperiment() throws Exception {
        MvcResult created = mockMvc.perform(as(post("/api/experiments"), "operator-demo", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(latencyExperimentPayload()))
                .andExpect(status().isCreated())
                .andReturn();
        return readField(created.getResponse().getContentAsString(), "id");
    }

    private static String latencyExperimentPayload() {
        return """
                {
                  "name": "Checkout latency envelope",
                  "description": "Inject 350ms latency into a guarded checkout canary.",
                  "targetSelector": {
                    "service": "checkout-api",
                    "namespace": "payments",
                    "labels": {
                      "lane": "canary"
                    },
                    "tags": ["checkout", "payments"]
                  },
                  "faultConfig": {
                    "type": "latency",
                    "durationSeconds": 480,
                    "parameters": {
                      "latencyMs": 350,
                      "percentage": 30
                    }
                  },
                  "safetyRules": {
                    "abortConditions": ["Abort if 5xx exceeds 2.5% for 90 seconds"],
                    "maxAffectedTargets": 2,
                    "approvalRequired": false,
                    "rollbackMode": "automatic"
                  },
                  "environmentMetadata": {
                    "environment": "staging",
                    "region": "us-phoenix-1",
                    "team": "payments"
                  }
                }
                """;
    }

    private static String updatedLatencyExperimentPayload() {
        return """
                {
                  "name": "Checkout latency envelope",
                  "description": "Inject 500ms latency into a guarded checkout canary lane.",
                  "targetSelector": {
                    "service": "checkout-api",
                    "namespace": "payments",
                    "cluster": "staging-west",
                    "labels": {
                      "lane": "canary"
                    }
                  },
                  "faultConfig": {
                    "type": "latency",
                    "durationSeconds": 600,
                    "parameters": {
                      "latencyMs": 500,
                      "percentage": 20,
                      "jitterMs": 50
                    }
                  },
                  "safetyRules": {
                    "abortConditions": ["Abort if 5xx exceeds 2.5% for 90 seconds"],
                    "maxAffectedTargets": 1,
                    "approvalRequired": true,
                    "rollbackMode": "manual"
                  },
                  "environmentMetadata": {
                    "environment": "staging",
                    "region": "us-phoenix-1",
                    "team": "payments"
                  }
                }
                """;
    }
}
