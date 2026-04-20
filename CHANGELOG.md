# Changelog

All notable changes to this project are tracked in this file.

## Unreleased

- MYG-12: added scoped HTTP error injection runs for `500` and `503` responses, including traffic percentage and route filters, timed or manual rollback handling, runtime execution reports, and audit visibility. Testing note: run `mvn -q test`.
- MYG-28: standardized the local developer workflow around Docker Compose for PostgreSQL, Redis, and RabbitMQ; verified bootstrap seeding for sample agents; and added reset, health-check, and troubleshooting documentation. Testing note: run `make test-local`, then use `make bootstrap-local` and `make local-health` for the local stack smoke check.
