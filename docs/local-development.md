# Local Development

This project includes a local developer stack for the control plane and its first dependency set.

## Prerequisites

- Docker with Compose support
- Java 17
- Maven 3.9+

## Bootstrap

Start PostgreSQL, Redis, and RabbitMQ, wait for them to report healthy, and verify that PostgreSQL contains the seeded sample agents:

```bash
make bootstrap-local
```

The bootstrap script provisions:

- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`
- RabbitMQ AMQP on `localhost:5672`
- RabbitMQ management UI on `http://localhost:15672`

PostgreSQL is initialized from [`infra/postgres/init/01_schema.sql`](../infra/postgres/init/01_schema.sql) and [`infra/postgres/init/02_sample_agents.sql`](../infra/postgres/init/02_sample_agents.sql). The seed creates three sample agents so `/agents` returns useful data on the first local run.

## Run The App

Start the Spring Boot service against the local profile:

```bash
make run-local
```

`make run-local` uses [`scripts/run-local.sh`](../scripts/run-local.sh), which defaults to `SPRING_PROFILES_ACTIVE=local` and port `8080`. Override the port if needed:

```bash
PORT=8090 make run-local
```

## Health Checks

Validate the stack after the app is running:

```bash
make local-health
```

The health check reports:

- `docker compose ps` for PostgreSQL, Redis, and RabbitMQ
- the seeded row count in PostgreSQL
- a `PING` against Redis
- a diagnostic ping against RabbitMQ
- the Spring Boot actuator health document when the app is up

If you start the app on a non-default port, pass the URL explicitly:

```bash
APP_URL=http://localhost:8090 make local-health
```

## Reset

Blow away the local volumes and re-seed the stack from scratch:

```bash
make local-reset
make bootstrap-local
```

`make local-reset` runs `docker compose down -v --remove-orphans`, which removes the PostgreSQL data volume so the init scripts replay on the next bootstrap.

## Common Failures

- PostgreSQL connection failures on app startup usually mean the dependencies are not ready yet. Re-run `make bootstrap-local` and wait for the dependency checks to finish before retrying `make run-local`.
- A seeded agent count of `0` means the PostgreSQL volume was created without the init scripts. Run `make local-reset` and then `make bootstrap-local` to rebuild the database from the checked-in SQL files.
- If `make local-health` cannot reach the app but Docker services are healthy, confirm the app is running with the `local` profile and that the port in `APP_URL` matches the one used for startup.
- If Docker reports a port conflict on `5432`, `6379`, `5672`, or `15672`, stop the conflicting local service or override the port mapping before retrying the bootstrap.
