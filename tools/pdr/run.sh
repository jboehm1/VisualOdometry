#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
d="$(mktemp -d)";trap 'rm -rf "$d"' EXIT
javac -encoding UTF-8 -d "$d" app/src/main/java/dev/jboehm/vectorvio/pdr/{PdrEngine,StepPeakDetector}.java tools/pdr/PdrReplay.java
java -cp "$d" dev.jboehm.vectorvio.pdr.PdrReplay "${1:-tools/pdr/fixtures/walk-east.csv}"
