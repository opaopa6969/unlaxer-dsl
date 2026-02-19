#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

DOCS=(
  "$ROOT_DIR/README.md"
  "$ROOT_DIR/README.ja.md"
  "$ROOT_DIR/SPEC.md"
)

OPTIONS=(
  '--grammar'
  '--output'
  '--generators'
  '--validate-only'
  '--report-format'
  '--report-file'
  '--report-version'
  '--report-schema-check'
)

for doc in "${DOCS[@]}"; do
  if [[ ! -f "$doc" ]]; then
    echo "[spec/check-doc-sync] ERROR: missing doc file: $doc" >&2
    echo "[spec/check-doc-sync] Fix: restore the missing documentation file." >&2
    exit 1
  fi
  for opt in "${OPTIONS[@]}"; do
    if ! grep -Fq -- "$opt" "$doc"; then
      echo "[spec/check-doc-sync] ERROR: option '$opt' is missing in $(basename "$doc")" >&2
      echo "[spec/check-doc-sync] Fix: add '$opt' to the CLI options section in that doc." >&2
      exit 1
    fi
  done

done

echo "[spec/check-doc-sync] OK: CLI option docs are synchronized across README.md, README.ja.md, and SPEC.md."
