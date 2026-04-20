SHELL := /bin/bash

.PHONY: bootstrap-local run-local local-health local-reset

bootstrap-local:
	./scripts/local/bootstrap.sh

run-local:
	SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

local-health:
	./scripts/local/check-health.sh

local-reset:
	./scripts/local/reset.sh
