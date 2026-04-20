#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_URL="${APP_URL:-http://localhost:8080}"

cd "$ROOT_DIR"

echo "Docker services:"
docker compose ps

echo
echo "Seeded agents in PostgreSQL:"
docker compose exec -T postgres psql -U chaos -d chaos_control_plane -Atc "SELECT COUNT(*) FROM agents;"

echo
echo "Redis ping:"
docker compose exec -T redis redis-cli ping

echo
echo "RabbitMQ ping:"
docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping

echo
if curl -fsS "$APP_URL/actuator/health" >/dev/null 2>&1; then
    echo "Spring Boot health:"
    curl -fsS "$APP_URL/actuator/health"
    echo
else
    echo "Spring Boot app is not running on $APP_URL"
fi
