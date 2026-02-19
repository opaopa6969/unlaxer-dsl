#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

cd "$ROOT_DIR"

./scripts/check-scripts.sh
mvn -q test
./scripts/check-spec-json-examples.sh

echo "[check-all] OK: all local checks passed."
