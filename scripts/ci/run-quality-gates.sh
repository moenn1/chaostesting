#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

echo "Checking shell script syntax..."
while IFS= read -r script; do
    bash -n "$script"
done < <(find scripts -type f -name '*.sh' | sort)

echo "Running static UI smoke checks..."
"$ROOT_DIR/scripts/ci/check-static-assets.sh"

echo "Running Maven verify..."
mvn -B -ntp verify

echo "Running docs and changelog gate..."
if [ "${1:-}" != "" ] && [ "${2:-}" != "" ]; then
    "$ROOT_DIR/scripts/ci/check-docs-changelog.sh" "$1" "$2"
else
    "$ROOT_DIR/scripts/ci/check-docs-changelog.sh"
fi

echo "All local quality gates passed."
