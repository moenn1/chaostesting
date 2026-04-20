# Chaos Platform

Spring Boot control-plane service for the Chaos Platform.

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

Check local health after the app is running:

```bash
make local-health
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

## Default local endpoints

- App: `http://localhost:8080`
- App health: `http://localhost:8080/actuator/health`
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- RabbitMQ AMQP: `localhost:5672`
- RabbitMQ UI: `http://localhost:15672` (`chaos` / `chaos`)

## Live run monitoring

The static app shell now includes a live run screen at `http://localhost:8080/live-runs/`. This ticket keeps the work frontend-scoped: the page uses stubbed run, agent, timeline, and stop-confirmation state so the layout and operator workflow can be reviewed before the blocked run timeline contract is ready.

What is available in the current shell:

- The run list, detail card, agent progress, and execution timeline all stay in a stable two-column operator frame.
- The refresh action and background pulse simulate a live screen without calling backend timeline endpoints.
- The stop panel captures a local confirmation state so reviewers can validate the interaction flow before real stop events are wired.
- Environment switching still scopes the live-run scenarios to the selected shell context.

## Troubleshooting

- If `make run-local` fails with a PostgreSQL connection error, rerun `make bootstrap-local` and wait for the dependency checks to pass.
- If sample agents are missing, reset the stack with `make local-reset` so PostgreSQL replays the init scripts on a fresh volume.
- If port `5432`, `6379`, `5672`, or `15672` is already in use, stop the local conflicting service before bringing the stack up.
