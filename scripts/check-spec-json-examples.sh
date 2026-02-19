#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SPEC_FILE="$ROOT_DIR/SPEC.md"
BEFORE_HASH="$(sha256sum "$SPEC_FILE" | awk '{print $1}')"

if [[ -d "$ROOT_DIR/target/classes" ]]; then
  "$ROOT_DIR/scripts/refresh-spec-json-examples.sh" --skip-build
else
  "$ROOT_DIR/scripts/refresh-spec-json-examples.sh"
fi

AFTER_HASH="$(sha256sum "$SPEC_FILE" | awk '{print $1}')"
if [[ "$BEFORE_HASH" == "$AFTER_HASH" ]]; then
  echo "[check-spec-json-examples] OK: SPEC.md JSON examples are up to date."
  exit 0
fi

echo "[check-spec-json-examples] OUTDATED: SPEC.md JSON examples differ from generated output." >&2
echo "Run: ./scripts/refresh-spec-json-examples.sh" >&2
git -C "$ROOT_DIR" --no-pager diff -- "$SPEC_FILE" >&2
exit 1
