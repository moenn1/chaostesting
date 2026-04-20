# Chaos Platform

Spring Boot control-plane service for the Chaos Platform.

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

- `VIEWER`: dashboard and static UI routes, `/auth/me`, audit history, run status, kill-switch status, agent read APIs, and `GET /api/experiments`.
- `OPERATOR`: experiment CRUD writes, dispatch validation, run authorization, and `/safety/runs/{runId}/stop`.
- `APPROVER`: `/safety/approvals`.
- `ADMIN`: kill-switch enable and disable, plus all viewer/operator/approver capabilities.

Machine registration endpoints `/agents/register` and `/agents/heartbeat` stay open for now so the current runtime continues working. They are intentionally left for the signed agent-auth hardening tracked in `MYG-44`.

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

The full role-to-route matrix and OIDC mapping notes are documented in `docs/security-auth.md`.

Check local health after the app is running:

```bash
make local-health
```

Run the automated test suite:

```bash
make test-local
```

The local bootstrap initializes PostgreSQL, Redis, and RabbitMQ. PostgreSQL is pre-seeded with sample agents so the first API tickets can query real data immediately after the app starts.

Verify the seeded agents:

```bash
curl -s http://localhost:8080/agents
```

Reset the local stack and re-run the seed scripts:

```bash
make local-reset
make bootstrap-local
```

Full step-by-step bootstrap, reset, and troubleshooting notes live in `docs/local-development.md`.

## Default local endpoints

- App: `http://localhost:8080`
- App health: `http://localhost:8080/actuator/health`
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- RabbitMQ AMQP: `localhost:5672`
- RabbitMQ UI: `http://localhost:15672` (`chaos` / `chaos`)

## Operator UI routes

The MVP app shell exposes route-specific operator views under the default app URL:

- Dashboard: `http://localhost:8080/`
- Experiment builder: `http://localhost:8080/experiments/`
- Live runs: `http://localhost:8080/live-runs/`
- Results: `http://localhost:8080/results/`
- History: `http://localhost:8080/history/`

### Experiment builder capabilities

The experiment builder route now supports the current MVP configuration flow for chaos drills:

- Create a new draft or duplicate an existing draft from the route-local catalog.
- Edit target selectors for service name, namespace, service tags, and environment targeting.
- Configure either fixed latency injection or HTTP error injection with `500` or `503` responses.
- Adjust safety constraints before save, including duration limit, rollout strategy, guardrail copy, approval requirement, and environment allowlist coverage.
- Review the generated save checklist and payload preview before storing a local draft snapshot.

Builder saves are stored in browser local storage, so refreshes preserve the last saved draft set for the current browser profile.

## Troubleshooting

- If `make run-local` fails with a PostgreSQL connection error, rerun `make bootstrap-local` and wait for the dependency checks to pass.
- If sample agents are missing, reset the stack with `make local-reset` so PostgreSQL replays the init scripts on a fresh volume.
- If port `5432`, `6379`, `5672`, or `15672` is already in use, stop the local conflicting service before bringing the stack up.
- If `docker compose` times out while pulling images from Docker Hub, configure the Docker daemon or Docker Desktop proxy settings separately from your shell env vars, then rerun `make bootstrap-local`.
