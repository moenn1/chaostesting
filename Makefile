SHELL := /bin/bash

.PHONY: bootstrap-local run-local test-local ci-local local-health local-reset

bootstrap-local:
	./scripts/local/bootstrap.sh

run-local:
	./scripts/run-local.sh

test-local:
	./scripts/test-local.sh

ci-local:
	./scripts/ci/run-quality-gates.sh

local-health:
	./scripts/local/check-health.sh

local-reset:
	./scripts/local/reset.sh
