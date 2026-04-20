#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

echo "Docker services:"
docker compose ps

echo
echo "Seeded agents in PostgreSQL:"
docker compose exec -T postgres psql -U chaos -d chaos_control_plane -Atc "SELECT COUNT(*) FROM agents;"

echo
if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "Spring Boot health:"
    curl -fsS http://localhost:8080/actuator/health
    echo
else
    echo "Spring Boot app is not running on http://localhost:8080"
fi
