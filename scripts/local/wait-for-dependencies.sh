#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

wait_for() {
    local label="$1"
    shift

    for attempt in $(seq 1 30); do
        if "$@" >/dev/null 2>&1; then
            echo "$label is ready"
            return 0
        fi
        sleep 2
    done

    echo "$label did not become ready in time" >&2
    return 1
}

wait_for "PostgreSQL" docker compose exec -T postgres pg_isready -U chaos -d chaos_control_plane
wait_for "Redis" docker compose exec -T redis redis-cli ping
wait_for "RabbitMQ" docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping
