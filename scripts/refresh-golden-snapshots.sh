#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

CP_FILE="/tmp/unlaxer-dsl-test-cp.txt"

mvn -q -DskipTests test-compile
mvn -q -DincludeScope=test -Dmdep.outputFile="$CP_FILE" dependency:build-classpath >/dev/null

CP="target/classes:target/test-classes:$(cat "$CP_FILE")"

java --enable-preview -cp "$CP" org.unlaxer.dsl.codegen.SnapshotFixtureWriter

echo "Golden snapshots refreshed under src/test/resources/golden"
