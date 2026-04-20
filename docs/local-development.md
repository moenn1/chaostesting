# Setup Guide

This guide separates the local platform into three tracks:

- Contributors: bootstrap the stack, run the service, and verify changes.
- Operators: exercise the authenticated APIs, seeded agents, approvals, kill-switch flows, and HTTP error drills.
- Demo or evaluation users: use the static UI routes and the curated walkthroughs without reverse-engineering the codebase.

Pair this document with [Platform architecture](platform-architecture.md) and [Example walkthroughs](example-walkthroughs.md).

## Shared prerequisites

- Docker with Compose support
- Java 17
- Maven 3.9+

If you are behind the project proxy, export these variables before `git`, `curl`, `mvn`, `npm`, or other networked shell commands:

```bash
export https_proxy=http://www-proxy-phx.oraclecorp.com:80
export http_proxy=http://www-proxy-phx.oraclecorp.com:80
export HTTPS_PROXY=http://www-proxy-phx.oraclecorp.com:80
export HTTP_PROXY=http://www-proxy-phx.oraclecorp.com:80
export no_proxy='*.us.oracle.com,localhost,127.0.0.1'
export NO_PROXY='*.us.oracle.com,localhost,127.0.0.1'
```

[`scripts/run-local.sh`](../scripts/run-local.sh) and [`scripts/test-local.sh`](../scripts/test-local.sh) already export these values for local app start and test runs. Docker image pulls still depend on Docker daemon or Docker Desktop proxy settings.

## Infrastructure bootstrap

Start PostgreSQL, Redis, and RabbitMQ, wait for them to report healthy, and verify that PostgreSQL contains the seeded sample agents:

```bash
make bootstrap-local
```

The bootstrap script provisions:

- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`
- RabbitMQ AMQP on `localhost:5672`
- RabbitMQ management UI on `http://localhost:15672`

PostgreSQL is initialized from [`infra/postgres/init/01_schema.sql`](../infra/postgres/init/01_schema.sql) and [`infra/postgres/init/02_sample_agents.sql`](../infra/postgres/init/02_sample_agents.sql). Those scripts only establish the sample agent seed needed before the app starts. The control-plane schema itself is owned by Flyway migrations in `src/main/resources/db/migration`, which run automatically when the service boots.

## Contributor setup

1. Bootstrap the dependency stack with `make bootstrap-local`.
2. Start the Spring Boot service with `make run-local`.
3. Confirm dependencies and app health with `make local-health`.
4. Run the automated tests with `make test-local`.
5. Run the same quality-gate bundle used by CI with `make ci-local`.

`make run-local` defaults to `SPRING_PROFILES_ACTIVE=local` and port `8080`. Useful overrides:

- `PORT`: app port for `make run-local`
- `SPRING_PROFILES_ACTIVE`: defaults to `local`
- `LOCAL_POSTGRES_HOST`, `LOCAL_POSTGRES_PORT`, `LOCAL_POSTGRES_USER`, `LOCAL_POSTGRES_PASSWORD`
- `LOCAL_REDIS_HOST`, `LOCAL_REDIS_PORT`
- `LOCAL_RABBITMQ_HOST`, `LOCAL_RABBITMQ_PORT`, `LOCAL_RABBITMQ_USER`, `LOCAL_RABBITMQ_PASSWORD`
- `APP_URL`: app URL for `make local-health`

Example alternate port:

```bash
PORT=8090 make run-local
APP_URL=http://localhost:8090 make local-health
```

## Operator setup

The local profile uses dev-mode auth. Requests are authenticated from:

- `X-Chaos-Dev-User`
- `X-Chaos-Dev-Roles`

If the headers are absent, the app falls back to `local-admin` with the `ADMIN` role. Start with a quick identity check:

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  http://localhost:8080/auth/me
```

List the seeded agents:

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  http://localhost:8080/agents
```

Register a fresh local agent when you want to exercise the agent lifecycle instead of relying on the seeded rows:

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  http://localhost:8080/agents/register \
  -d '{
    "name":"agent-local-1",
    "hostname":"developer-macbook",
    "environment":"staging",
    "region":"local",
    "supportedFaultCapabilities":["latency","http_error"]
  }'
```

Heartbeat that agent with the returned `id`:

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  http://localhost:8080/agents/heartbeat \
  -d '{
    "agentId":"<agent-id>"
  }'
```

### HTTP error drill

Validate a scoped HTTP error injection as an operator:

```bash
curl -s \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/dispatches/validate \
  -d '{
    "targetEnvironment": "staging",
    "targetSelector": "checkout-service",
    "faultType": "http_error",
    "requestedDurationSeconds": 120,
    "errorCode": 503,
    "trafficPercentage": 30,
    "routeFilters": ["/checkout"],
    "requestedBy": "operator-demo"
  }'
```

Dispatch the run once validation is allowed:

```bash
curl -s \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/dispatches \
  -d '{
    "targetEnvironment": "staging",
    "targetSelector": "checkout-service",
    "faultType": "http_error",
    "requestedDurationSeconds": 120,
    "errorCode": 503,
    "trafficPercentage": 30,
    "routeFilters": ["/checkout"],
    "requestedBy": "operator-demo"
  }'
```

Report the execution outcome. The stored actor comes from the authenticated principal, not from the payload:

```bash
curl -s \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: agent-eu-1' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/runs/<run-id>/reports \
  -d '{
    "state": "FAILURE",
    "message": "Failed to attach scoped route filter."
  }'
```

Stop the run manually if needed:

```bash
curl -s \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: ops-oncall' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/runs/<run-id>/stop \
  -d '{
    "reason": "customer-impact containment"
  }'
```

The authenticated operator routes are covered in [Security and RBAC](security-auth.md). The runnable end-to-end API flows are in [Example walkthroughs](example-walkthroughs.md).

## Demo and evaluation flow

Use these assets when you want a fast product tour instead of a full local drill:

- [Platform architecture](platform-architecture.md) for the current service topology and lifecycle flow.
- [Example walkthroughs](example-walkthroughs.md) for copy-paste API examples and HTTP error walkthroughs.
- Static UI routes under `http://localhost:8080/`
- Dashboard: `http://localhost:8080/`
- Experiment builder: `http://localhost:8080/experiments/`
- Live runs: `http://localhost:8080/live-runs/`
- Results: `http://localhost:8080/results/`
- History: `http://localhost:8080/history/`
- Agents: `http://localhost:8080/agents/`

The UI is a static shell on the current branch. It is useful for demos, route review, and payload previews, but it is not proof of live backend state.

## Reset

Blow away the local volumes and re-seed the stack from scratch:

```bash
make local-reset
make bootstrap-local
```

`make local-reset` runs `docker compose down -v --remove-orphans`, which removes the PostgreSQL data volume so the init scripts replay on the next bootstrap.

## Troubleshooting

- PostgreSQL connection failures on app startup usually mean the dependencies are not ready yet. Re-run `make bootstrap-local` and wait for the dependency checks to finish before retrying `make run-local`.
- A seeded agent count of `0` means the PostgreSQL volume was created without the init scripts. Run `make local-reset` and then `make bootstrap-local` to rebuild the database from the checked-in SQL files.
- If the app reports a Flyway validation or schema-history problem against an older local H2 file, delete `data/chaos-control-plane.mv.db` and restart so migrations can rebuild from a clean baseline.
- If `make local-health` cannot reach the app but Docker services are healthy, confirm the app is running with the `local` profile and that the port in `APP_URL` matches the one used for startup.
- If Docker reports a port conflict on `5432`, `6379`, `5672`, or `15672`, stop the conflicting local service or override the port mapping before retrying the bootstrap.
- If `make bootstrap-local` fails on image pulls with a timeout against `registry-1.docker.io`, update the Docker daemon or Docker Desktop proxy settings. Shell-level `HTTP_PROXY` exports are not always enough because image pulls are performed by the daemon, not the shell process.
- If Maven dependency resolution fails outside `make run-local` or `make test-local`, re-export the proxy block in your shell before retrying `mvn`.
- If agent registration works but `/agents` shows a stale status, send another heartbeat or check whether `agent-registry.stale-after` has elapsed.
