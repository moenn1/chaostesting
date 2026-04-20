# Changelog

All notable changes to this project are tracked in this file.

## Unreleased

- Added `MYG-4` experiment CRUD APIs with structured selector, fault, safety-rule, and environment metadata payloads plus field-level validation for invalid durations, unsupported fault parameters, and incomplete target selectors.
- MYG-28: standardized the local developer workflow around Docker Compose for PostgreSQL, Redis, and RabbitMQ; verified bootstrap seeding for sample agents; and added reset, health-check, and troubleshooting documentation. Testing note: run `make test-local`, then use `make bootstrap-local` and `make local-health` for the local stack smoke check.
- Added OIDC-backed login and explicit viewer/operator/approver/admin RBAC for the control plane.
- Added a development-only auth mode with header-based impersonation for local workflows.
- Added authenticated actor enforcement for approvals, dispatches, kill-switch actions, and direct run stop requests.
- MYG-18: replaced the `/experiments/` placeholder with an interactive builder that supports service, tag, namespace, and environment selectors; fixed-latency and HTTP `500`/`503` fault configuration; editable safety constraints; local draft save snapshots; and operator-facing payload preview and validation checklist UX. Testing note: run `node --check src/main/resources/static/app.js` and `./mvnw -q test`.
