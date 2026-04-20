# Changelog

All notable changes to this project are tracked in this file.

## Unreleased

- Added `MYG-6` run-stop state enforcement: dispatches now snapshot matching healthy agents as run assignments, manual and kill-switch stops persist terminal `STOPPED` runs with `endedAt`, and repeated stop attempts return a clear `409` validation payload.
- Added `MYG-4` experiment CRUD APIs with structured selector, fault, safety-rule, and environment metadata payloads plus field-level validation for invalid durations, unsupported fault parameters, and incomplete target selectors.
- MYG-28: standardized the local developer workflow around Docker Compose for PostgreSQL, Redis, and RabbitMQ; verified bootstrap seeding for sample agents; and added reset, health-check, and troubleshooting documentation. Testing note: run `make test-local`, then use `make bootstrap-local` and `make local-health` for the local stack smoke check.
- Added OIDC-backed login and explicit viewer/operator/approver/admin RBAC for the control plane.
- Added a development-only auth mode with header-based impersonation for local workflows.
- Added authenticated actor enforcement for approvals, dispatches, kill-switch actions, and direct run stop requests.
