#!/usr/bin/env bash
set -euo pipefail

export https_proxy=http://www-proxy-phx.oraclecorp.com:80
export http_proxy=http://www-proxy-phx.oraclecorp.com:80
export HTTPS_PROXY=http://www-proxy-phx.oraclecorp.com:80
export HTTP_PROXY=http://www-proxy-phx.oraclecorp.com:80
export no_proxy='*.us.oracle.com,localhost,127.0.0.1'
export NO_PROXY='*.us.oracle.com,localhost,127.0.0.1'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PORT="${PORT:-8080}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

cd "${PROJECT_ROOT}"
exec env SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE}" \
  mvn -q -DskipTests spring-boot:run -Dspring-boot.run.arguments="--server.port=${PORT}"
