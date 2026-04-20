# Changelog

All notable changes to this project are tracked in this file.

## Unreleased

- MYG-3: moved the control-plane schema onto Flyway, added first-class experiment/run/action/telemetry/audit tables with indexed foreign-key relationships for list/history/timeline queries, and added migration coverage for empty and pre-existing local databases.
- MYG-28: standardized the local developer workflow around Docker Compose for PostgreSQL, Redis, and RabbitMQ; verified bootstrap seeding for sample agents; and added reset, health-check, and troubleshooting documentation. Testing note: run `make test-local`, then use `make bootstrap-local` and `make local-health` for the local stack smoke check.
- Added OIDC-backed login and explicit viewer/operator/approver/admin RBAC for the control plane.
- Added a development-only auth mode with header-based impersonation for local workflows.
- Added authenticated actor enforcement for approvals, dispatches, kill-switch actions, and direct run stop requests.
