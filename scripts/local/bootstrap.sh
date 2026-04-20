#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

docker compose up -d postgres redis rabbitmq
"$ROOT_DIR/scripts/local/wait-for-dependencies.sh"

seed_count="$(docker compose exec -T postgres psql -U chaos -d chaos_control_plane -Atc "SELECT COUNT(*) FROM agents;")"
if ! [[ "$seed_count" =~ ^[0-9]+$ ]] || [ "$seed_count" -lt 1 ]; then
    echo "Expected seeded sample agents in PostgreSQL, but found '$seed_count'." >&2
    exit 1
fi

cat <<'EOF'
Local dependencies are ready.

Next steps:
  1. make run-local
  2. make local-health
  3. curl http://localhost:8080/agents
EOF

echo
echo "Sample agent rows seeded into PostgreSQL: $seed_count"
