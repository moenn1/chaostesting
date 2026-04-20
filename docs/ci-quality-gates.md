# CI Quality Gates

`MYG-38` adds branch validation for pull requests and `main` so the control plane only lands code that has already passed backend, static UI, and repo-hygiene checks.

## Workflow Summary

GitHub Actions now runs `.github/workflows/quality-gates.yml` for:

- pull requests that target `main`
- direct pushes to `main`

The workflow publishes separate statuses for:

- `Repo Hygiene`: enforces the repo policy that code, workflow, or infra changes must include both a `CHANGELOG.md` update and a documentation update in `docs/` or `README.md`.
- `Backend Verify`: runs `mvn -B -ntp verify` on Java 17 so compilation, packaging, and the Spring Boot test suite all pass together.
- `Static UI Smoke`: checks the static route shells, shared assets, and JavaScript syntax for `src/main/resources/static`.
- `Mainline Artifact`: runs only after the other jobs pass on pushes to `main`, packages the Spring Boot JAR, and uploads it as a workflow artifact.

## Local Verification

Run the same local quality-gate bundle before opening or updating a pull request:

```bash
make ci-local
```

`make ci-local` runs:

1. shell syntax validation for every checked-in script under `scripts/`
2. the static UI smoke checks in `scripts/ci/check-static-assets.sh`
3. `mvn -B -ntp verify`
4. the docs/changelog policy check in `scripts/ci/check-docs-changelog.sh`

If you want to validate the docs/changelog gate against a specific diff range, pass refs directly:

```bash
./scripts/ci/check-docs-changelog.sh origin/main HEAD
```

## Required Branch Checks

When branch protection is configured for `main`, mark these jobs as required:

- `Repo Hygiene`
- `Backend Verify`
- `Static UI Smoke`

`Mainline Artifact` should remain informational because it only runs after a successful push to `main`.
