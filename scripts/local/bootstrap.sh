#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

docker compose up -d postgres redis rabbitmq
"$ROOT_DIR/scripts/local/wait-for-dependencies.sh"

cat <<'EOF'
Local dependencies are ready.

Next steps:
  1. make run-local
  2. make local-health
  3. curl http://localhost:8080/agents
EOF
