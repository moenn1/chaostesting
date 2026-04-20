#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATIC_DIR="$ROOT_DIR/src/main/resources/static"

required_pages=(
    "index.html"
    "experiments/index.html"
    "live-runs/index.html"
    "results/index.html"
    "history/index.html"
    "agents/index.html"
)

fail() {
    echo "Static asset check failed: $1" >&2
    exit 1
}

[ -f "$STATIC_DIR/app.js" ] || fail "missing src/main/resources/static/app.js"
[ -f "$STATIC_DIR/styles.css" ] || fail "missing src/main/resources/static/styles.css"

for page in "${required_pages[@]}"; do
    file="$STATIC_DIR/$page"
    [ -f "$file" ] || fail "missing $page"
    grep -q '<link rel="stylesheet" href="/styles.css"' "$file" || fail "$page must load /styles.css"
    grep -q '<script type="module" src="/app.js"></script>' "$file" || fail "$page must load /app.js"
    grep -q 'data-route="' "$file" || fail "$page must declare a data-route"
    grep -q 'id="content"' "$file" || fail "$page must include the shared content mount"
done

route_patterns=(
    "dashboard:"
    "experiments:"
    "'live-runs':"
    "results:"
    "history:"
    "agents:"
)

for pattern in "${route_patterns[@]}"; do
    grep -q "$pattern" "$STATIC_DIR/app.js" || fail "app.js is missing the ${pattern} route definition"
done

grep -q 'const routes =' "$STATIC_DIR/app.js" || fail "app.js must define the route catalog"
grep -q '\.shell {' "$STATIC_DIR/styles.css" || fail "styles.css must define the shared shell layout"
grep -q -- '--accent:' "$STATIC_DIR/styles.css" || fail "styles.css must keep the design token palette"

if command -v node >/dev/null 2>&1; then
    node --check "$STATIC_DIR/app.js"
else
    echo "Skipping JavaScript syntax check because node is not installed."
fi

echo "Static asset smoke checks passed."
