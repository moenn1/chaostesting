# Chaos Platform

Spring Boot control-plane service for the Chaos Platform.

## Documentation map

- [Platform architecture](docs/platform-architecture.md): control-plane components, execution model, storage layout, event-bus status, and observability surfaces on the current `main` branch.
- [Setup guide](docs/local-development.md): contributor, operator, and demo/evaluation paths with bootstrap, environment variables, proxy notes, and troubleshooting.
- [Example walkthroughs](docs/example-walkthroughs.md): copy-paste API scenarios for latency drills, approval-gated production runs, run analysis, and scoped HTTP error flows.
- [Security and RBAC](docs/security-auth.md): OIDC mode, local dev auth mode, and route permissions.
- [CI quality gates](docs/ci-quality-gates.md): local and GitHub Actions verification workflow.

## Authentication and RBAC

The platform now defaults to `chaos.auth.mode=oidc` outside local development. Configure your OIDC client registration with Spring Security and expose a claim that contains one or more of these role names:

- `VIEWER`
- `OPERATOR`
- `APPROVER`
- `ADMIN`

The default claim mapping expects:

- role claim: `groups`
- principal claim: `preferred_username`

Route permissions are enforced consistently across the control plane:

- `VIEWER`: dashboard and static UI routes, including `/experiments/**`, `/live-runs/**`, `/results/**`, `/history/**`, and `/fleet/**`, plus `/auth/me`, audit history, run status, kill-switch status, agent read APIs, and `GET /api/experiments`.
- `OPERATOR`: experiment CRUD writes, `POST /api/experiments/{experimentId}/runs`, dispatch validation, run authorization, and `/safety/runs/{runId}/stop`.
- `APPROVER`: `/safety/approvals`.
- `ADMIN`: kill-switch enable and disable, plus all viewer/operator/approver capabilities.

Machine registration endpoints `/agents/register` and `/agents/heartbeat` stay open for now so the current runtime continues working. They are intentionally left for the signed agent-auth hardening tracked in `MYG-44`.

## Run stop semantics

Run authorization now snapshots the healthy agents that match the run environment and requested fault capability. Those assignments stay attached to the run record so stop operations can update both the run and its targeted agent set together.

`POST /safety/runs/{runId}/stop` now enforces an explicit state machine:

- only `ACTIVE` runs can be stopped
- successful stops return terminal `ROLLED_BACK` status plus `endedAt`, `rollbackVerifiedAt`, `assignmentCount`, `activeAssignmentCount`, and `stoppedAssignmentCount`
- repeated stop requests against `STOP_REQUESTED`, `ROLLED_BACK`, `STOPPED`, or `COMPLETED` runs return `409 Conflict` with a structured validation body describing the current status

Kill-switch responses expose both `rolledBackRunCount` and `stopRequestsIssued` so dashboards can distinguish terminal cleanup from in-flight stop activity, while still leaving `stoppedRunCount` available for compatibility with any future direct-stop flows.

## Local development

Prerequisites:

- Docker with Compose support
- Java 17
- Maven 3.9+

Bootstrap the local dependencies:

```bash
make bootstrap-local
```

Start the application against the local stack:

```bash
make run-local
```

The local profile enables the documented development-only auth mode:

- default user: `local-admin`
- default role: `ADMIN`
- impersonation headers: `X-Chaos-Dev-User` and `X-Chaos-Dev-Roles`

Example requests:

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  http://localhost:8080/auth/me
```

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/dispatches/validate \
  -d '{"targetEnvironment":"staging","targetSelector":"checkout-service","faultType":"latency","requestedDurationSeconds":120}'
```

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/api/experiments \
  -d '{
    "name":"Checkout latency envelope",
    "description":"Inject 350ms latency into a guarded checkout canary.",
    "targetSelector":{
      "service":"checkout-api",
      "namespace":"payments",
      "labels":{"lane":"canary"}
    },
    "faultConfig":{
      "type":"latency",
      "durationSeconds":480,
      "parameters":{"latencyMs":350,"percentage":30}
    },
    "safetyRules":{
      "abortConditions":["Abort if 5xx exceeds 2.5% for 90 seconds"],
      "maxAffectedTargets":2,
      "approvalRequired":false,
      "rollbackMode":"automatic"
    },
    "environmentMetadata":{
      "environment":"staging",
      "region":"us-phoenix-1",
      "team":"payments"
    }
  }'
```

Start a manual run from a saved experiment. The first call creates a running record with `startedAt`, a persisted target snapshot, and a run-start lifecycle event. Repeating the same request while that experiment still has an active run returns the existing run instead of creating a duplicate:

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/api/experiments/<experiment-id>/runs \
  -d '{}'
```

For production-like environments that require an approval, provide the previously issued approval id:

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/api/experiments/<experiment-id>/runs \
  -d '{"approvalId":"<approval-id>"}'
```

Traffic-shaping dispatches also support jittered latency envelopes, bounded latency ranges, and request-drop runs:

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/dispatches \
  -d '{
    "targetEnvironment":"staging",
    "targetSelector":"edge-gateway",
    "faultType":"request_drop",
    "requestedDurationSeconds":180,
    "dropPercentage":12,
    "requestedBy":"operator-demo"
  }'
```

For random latency envelopes, either use `latencyMilliseconds` with `latencyJitterMilliseconds` or use `latencyMinimumMilliseconds` plus `latencyMaximumMilliseconds`.

The full role-to-route matrix and OIDC mapping notes are documented in `docs/security-auth.md`.

## Run observability APIs

Completed and active runs now expose three analysis surfaces for the results UI and operator workflows:

- `GET /safety/runs/{runId}/metrics` for chart-friendly summary metrics and ordered telemetry points.
- `GET /safety/runs/{runId}/traces` for run-scoped lifecycle trace summaries derived from audit and telemetry records.
- `GET /safety/runs/{runId}/diagnostics` for a downloadable JSON bundle that packages the run record, metrics, traces, telemetry, and audit history.

Check local health after the app is running:

```bash
make local-health
```

Run the automated test suite:

```bash
make test-local
```

Run the same repo quality-gate bundle that backs GitHub Actions:

```bash
make ci-local
```

The local bootstrap initializes PostgreSQL, Redis, and RabbitMQ. PostgreSQL is pre-seeded with sample agents so the first API tickets can query real data immediately after the app starts.

The control-plane schema now lives under Flyway migrations in `src/main/resources/db/migration`. The local bootstrap still pre-seeds PostgreSQL with sample agents so the first API tickets can query real data immediately after the app starts, and the app applies the remaining schema migrations when it connects.

Verify the seeded agents:

```bash
curl -s http://localhost:8080/agents
```

Reset the local stack and re-run the seed scripts:

```bash
make local-reset
make bootstrap-local
```

Full setup, architecture, and walkthrough notes live in `docs/local-development.md`, `docs/platform-architecture.md`, and `docs/example-walkthroughs.md`.
CI workflow and branch-gate details live in `docs/ci-quality-gates.md`.

## HTTP Error Injection

The control plane now supports scoped HTTP error injection runs for `500` and `503` responses. Dispatches can set a traffic percentage plus optional route filters, and each run exposes stop and execution-report endpoints for success, failure, and rollback visibility.

Example dispatch:

```bash
curl -s http://localhost:8080/safety/dispatches \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  -d '{
    "targetEnvironment": "staging",
    "targetSelector": "checkout-service",
    "faultType": "http_error",
    "requestedDurationSeconds": 120,
    "errorCode": 503,
    "trafficPercentage": 30,
    "routeFilters": ["/checkout"],
    "requestedBy": "experiment-operator"
  }'
```

Example execution report:

```bash
curl -s http://localhost:8080/safety/runs/<run-id>/reports \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  -d '{
    "state": "FAILURE",
    "message": "Failed to attach scoped route filter."
  }'
```

## Default local endpoints

- App: `http://localhost:8080`
- App health: `http://localhost:8080/actuator/health`
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- RabbitMQ AMQP: `localhost:5672`
- RabbitMQ UI: `http://localhost:15672` (`chaos` / `chaos`)

## Operator UI routes

The current app shell exposes route-specific operator views under the default app URL:

- Dashboard: `http://localhost:8080/`
- Experiment builder: `http://localhost:8080/experiments/`
- Live runs: `http://localhost:8080/live-runs/`
- Results: `http://localhost:8080/results/`
- History: `http://localhost:8080/history/`

### Experiment builder capabilities

The experiment builder route now supports the current configuration flow for chaos drills:

- Create a new draft or duplicate an existing draft from the route-local catalog.
- Edit target selectors for service name, namespace, service tags, and environment targeting.
- Configure either fixed latency injection or HTTP error injection with `500` or `503` responses.
- Adjust safety constraints before save, including duration limit, rollout strategy, guardrail copy, approval requirement, and environment allowlist coverage.
- Review the generated save checklist and payload preview before storing a local draft snapshot.

Builder saves are stored in browser local storage, so refreshes preserve the last saved draft set for the current browser profile.

### Live run monitoring

The static app shell also includes a live run screen at `http://localhost:8080/live-runs/`. The current route is intentionally frontend-scoped: it uses stubbed run, agent, timeline, and stop-confirmation state so the operator workflow can be reviewed before backend timeline endpoints are wired.

- The run list, detail card, agent progress, and execution timeline stay in a stable two-column operator frame.
- The refresh action and background pulse simulate a live screen without calling backend timeline endpoints.
- The stop panel keeps a local confirmation state in-frame so reviewers can validate the rollback interaction without leaving the page.
- Environment switching still scopes the live-run scenarios to the selected shell context.

## Troubleshooting

- If `make run-local` fails with a PostgreSQL connection error, rerun `make bootstrap-local` and wait for the dependency checks to pass.
- If sample agents are missing, reset the stack with `make local-reset` so PostgreSQL replays the init scripts on a fresh volume.
- If the app fails startup on an older local database, rerun it once so Flyway can baseline the schema, or remove the local `data/chaos-control-plane.mv.db` file if you want a fully clean H2 reset.
- If port `5432`, `6379`, `5672`, or `15672` is already in use, stop the local conflicting service before bringing the stack up.
- If `docker compose` times out while pulling images from Docker Hub, configure the Docker daemon or Docker Desktop proxy settings separately from your shell env vars, then rerun `make bootstrap-local`.
