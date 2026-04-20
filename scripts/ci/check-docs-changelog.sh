#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HEAD_REF="${2:-HEAD}"

cd "$ROOT_DIR"

if [ "${1:-}" != "" ]; then
    BASE_REF="$1"
elif git rev-parse --verify origin/main >/dev/null 2>&1; then
    BASE_REF="origin/main"
else
    BASE_REF="HEAD~1"
fi

if ! git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
    echo "Unable to resolve base ref '$BASE_REF' for docs/changelog checks." >&2
    exit 1
fi

merge_base="$(git merge-base "$BASE_REF" "$HEAD_REF")"
changed_files=()

add_changed_file() {
    local candidate="$1"
    local existing
    if [ -z "$candidate" ]; then
        return
    fi
    if [ "${#changed_files[@]}" -gt 0 ]; then
        for existing in "${changed_files[@]}"; do
            if [ "$existing" = "$candidate" ]; then
                return
            fi
        done
    fi
    changed_files+=("$candidate")
}

while IFS= read -r file; do
    add_changed_file "$file"
done < <(git diff --name-only "$merge_base" "$HEAD_REF")

if [ "$HEAD_REF" = "HEAD" ]; then
    while IFS= read -r file; do
        add_changed_file "$file"
    done < <(git diff --cached --name-only)

    while IFS= read -r file; do
        add_changed_file "$file"
    done < <(git diff --name-only)

    while IFS= read -r file; do
        add_changed_file "$file"
    done < <(git ls-files --others --exclude-standard)
fi

if [ "${#changed_files[@]}" -eq 0 ]; then
    echo "No changed files detected between $merge_base and $HEAD_REF."
    exit 0
fi

requires_docs_and_changelog=0
has_docs_update=0
has_changelog_update=0

echo "Evaluating docs/changelog gate for changes since $merge_base:"
for file in "${changed_files[@]}"; do
    echo "  $file"
    case "$file" in
        CHANGELOG.md)
            has_changelog_update=1
            ;;
    esac
    case "$file" in
        README.md|docs/*)
            has_docs_update=1
            ;;
    esac
    case "$file" in
        .github/*|src/*|scripts/*|infra/*|pom.xml|Makefile|docker-compose*.yml)
            requires_docs_and_changelog=1
            ;;
    esac
done

if [ "$requires_docs_and_changelog" -eq 0 ]; then
    echo "No code, workflow, or infrastructure changes detected; docs/changelog gate is satisfied."
    exit 0
fi

if [ "$has_docs_update" -ne 1 ] || [ "$has_changelog_update" -ne 1 ]; then
    echo "Code, workflow, or infrastructure changes must include both:" >&2
    echo "  - a docs update under docs/ or README.md" >&2
    echo "  - a CHANGELOG.md update" >&2
    exit 1
fi

echo "Docs/changelog gate satisfied."
