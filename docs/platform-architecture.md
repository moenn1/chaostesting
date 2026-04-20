# Platform Architecture

This document describes the current `main` branch architecture for the Chaos Platform. It focuses on what is already implemented in this repo and calls out where local infrastructure is present before the application wiring lands.

## System map

```text
contributors / operators / demo users
            |
     browser or curl
            |
  Spring Boot control plane
  - experiment CRUD
  - safety validation and dispatch
  - approvals and kill switch
  - agent registry
  - static operator UI
  - in-process scheduler
            |
            +--> H2 by default, PostgreSQL in local profile
            |    - experiments
            |    - runs
            |    - latency telemetry snapshots
            |    - audit records
            |    - approvals
            |    - kill-switch state
            |    - agents
            |
            +--> HTTP agent registration and heartbeat
            |
            +--> actuator health and audit/telemetry read APIs

local-only dependencies provisioned today:
- Redis
- RabbitMQ

These services are part of bootstrap and health checks, but the current
application code does not yet publish or consume messages through them.
```

## Control plane

The control plane is a single Spring Boot service defined in [`src/main/java/com/myg/controlplane/ChaosPlatformApplication.java`](../src/main/java/com/myg/controlplane/ChaosPlatformApplication.java). It owns:

- Experiment authoring through [`/api/experiments`](../src/main/java/com/myg/controlplane/experiments/ExperimentController.java)
- Safety validation, approvals, run start, run stop, telemetry, audit reads, and kill switch through [`/safety`](../src/main/java/com/myg/controlplane/safety/RunDispatchController.java)
- Agent registration and heartbeat through [`/agents`](../src/main/java/com/myg/controlplane/agents/api/AgentController.java)
- Route-level security through [`SecurityConfiguration`](../src/main/java/com/myg/controlplane/security/SecurityConfiguration.java)
- Static operator-facing routes from [`src/main/resources/static`](../src/main/resources/static)

Runtime scheduling is in-process. [`ChaosPlatformApplication`](../src/main/java/com/myg/controlplane/ChaosPlatformApplication.java) creates a `ThreadPoolTaskScheduler`, and [`ChaosRunService`](../src/main/java/com/myg/controlplane/safety/ChaosRunService.java) uses it to emit periodic telemetry snapshots and to auto-rollback time-boxed runs.

## Execution and agent plane

The repo does not yet contain a separate execution-plane service or a message-bus-driven agent command path. The current execution model is:

1. An operator validates or authorizes a run through [`RunDispatchController`](../src/main/java/com/myg/controlplane/safety/RunDispatchController.java).
2. [`SafetyGuardrailsService`](../src/main/java/com/myg/controlplane/safety/SafetyGuardrailsService.java) evaluates environment policy, duration limits, kill-switch state, and approval requirements.
3. [`ChaosRunDispatchService`](../src/main/java/com/myg/controlplane/safety/ChaosRunDispatchService.java) hands the authorized request to [`ChaosRunService`](../src/main/java/com/myg/controlplane/safety/ChaosRunService.java).
4. [`ChaosRunService`](../src/main/java/com/myg/controlplane/safety/ChaosRunService.java) persists the run, writes the first telemetry snapshot, and schedules telemetry and rollback events locally.

Agents are currently modeled as registered HTTP workers. [`AgentRegistryService`](../src/main/java/com/myg/controlplane/agents/service/AgentRegistryService.java) tracks their last heartbeat and supported fault capabilities. The local bootstrap seeds three sample agents through [`infra/postgres/init/02_sample_agents.sql`](../infra/postgres/init/02_sample_agents.sql), which makes `/agents` useful immediately after setup.

## Storage and state

Storage is split by environment:

- Default runtime uses H2, configured in [`src/main/resources/application.yml`](../src/main/resources/application.yml).
- Local development switches to PostgreSQL in [`src/main/resources/application-local.yml`](../src/main/resources/application-local.yml).

The current persistent state includes:

- Experiments via [`ExperimentEntity`](../src/main/java/com/myg/controlplane/experiments/ExperimentEntity.java)
- Runs via [`ChaosRunEntity`](../src/main/java/com/myg/controlplane/safety/ChaosRunEntity.java)
- Latency telemetry snapshots via [`LatencyTelemetrySnapshotEntity`](../src/main/java/com/myg/controlplane/safety/LatencyTelemetrySnapshotEntity.java)
- Audit records via [`SafetyAuditRecordEntity`](../src/main/java/com/myg/controlplane/safety/SafetyAuditRecordEntity.java)
- Dispatch approvals via [`DispatchApprovalEntity`](../src/main/java/com/myg/controlplane/safety/DispatchApprovalEntity.java)
- Kill-switch state via [`KillSwitchStateEntity`](../src/main/java/com/myg/controlplane/safety/KillSwitchStateEntity.java)
- Agent registry records via [`AgentEntity`](../src/main/java/com/myg/controlplane/agents/infrastructure/AgentEntity.java)

The checked-in SQL in [`infra/postgres/init/01_schema.sql`](../infra/postgres/init/01_schema.sql) creates the agent registry tables, and [`infra/postgres/init/02_sample_agents.sql`](../infra/postgres/init/02_sample_agents.sql) seeds sample agent rows. The rest of the schema is created through JPA.

## Event bus and dependency status

The local stack provisions PostgreSQL, Redis, and RabbitMQ in [`docker-compose.yml`](../docker-compose.yml). On the current branch:

- PostgreSQL is active application storage.
- Redis is part of local parity and health checks, but there is no checked-in Redis-backed application flow yet.
- RabbitMQ is part of local parity and health checks, but there are no AMQP publishers, consumers, or command-channel handlers under `src/main/java`.

That means the platform can document the intended event-bus boundary today, but not a live RabbitMQ-backed execution loop yet.

## Observability and analysis surfaces

The current observability story on `main` is centered on health, auditability, and run telemetry:

- [`/actuator/health`](../src/main/java/com/myg/controlplane/security/SecurityConfiguration.java) confirms service health.
- [`/safety/runs/{runId}/telemetry`](../src/main/java/com/myg/controlplane/safety/RunDispatchController.java) returns the timeline of injection and rollback snapshots.
- [`/audit/events`](../src/main/java/com/myg/controlplane/safety/AuditLogController.java) and [`/safety/audit-records`](../src/main/java/com/myg/controlplane/safety/AuditLogController.java) expose the audit trail.
- Static UI routes under [`src/main/resources/static`](../src/main/resources/static) provide a demo shell for dashboards, experiments, results, history, and agent views.

[`AuditLogService`](../src/main/java/com/myg/controlplane/safety/AuditLogService.java) writes immutable action records for approvals, run starts, run stops, rollbacks, agent registration, and kill-switch changes. [`ChaosRunService`](../src/main/java/com/myg/controlplane/safety/ChaosRunService.java) writes telemetry snapshots at injection start, during scheduled runtime, and at rollback.

## Core lifecycle sequences

### Experiment authoring

1. `POST /api/experiments` reaches [`ExperimentController`](../src/main/java/com/myg/controlplane/experiments/ExperimentController.java).
2. [`ExperimentService`](../src/main/java/com/myg/controlplane/experiments/ExperimentService.java) validates selectors, fault parameters, safety rules, and environment metadata.
3. The normalized experiment is persisted and returned as structured JSON.

### Run validation and dispatch

1. `POST /safety/dispatches/validate` or `POST /safety/dispatches` reaches [`RunDispatchController`](../src/main/java/com/myg/controlplane/safety/RunDispatchController.java).
2. [`SafetyGuardrailsService`](../src/main/java/com/myg/controlplane/safety/SafetyGuardrailsService.java) enforces environment policy, duration caps, kill-switch state, and approval requirements.
3. Authorized runs are persisted by [`ChaosRunService`](../src/main/java/com/myg/controlplane/safety/ChaosRunService.java), which immediately writes an injection snapshot and schedules rollback.

### Run analysis and rollback

1. `GET /safety/runs/{runId}` and `GET /safety/runs/{runId}/telemetry` expose the current run state and snapshot history.
2. `POST /safety/runs/{runId}/stop` or the scheduled timeout marks the run for rollback.
3. [`ChaosRunService`](../src/main/java/com/myg/controlplane/safety/ChaosRunService.java) writes rollback telemetry and corresponding audit records.
4. `GET /safety/audit-records?resourceId=<run-id>` provides the operator-facing audit trail for the run.
