#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT_DIR/scripts/refresh-spec-json-examples.sh"

if git -C "$ROOT_DIR" diff --quiet -- SPEC.md; then
  echo "[check-spec-json-examples] OK: SPEC.md JSON examples are up to date."
  exit 0
fi

echo "[check-spec-json-examples] OUTDATED: SPEC.md JSON examples differ from generated output." >&2
echo "Run: ./scripts/refresh-spec-json-examples.sh" >&2
git -C "$ROOT_DIR" --no-pager diff -- SPEC.md >&2
exit 1
