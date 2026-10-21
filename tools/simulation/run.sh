#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
java com.sun.tools.javac.Main -d "$classes" \
  app/src/main/java/dev/jboehm/vectorvio/ReplayCamera.java \
  app/src/main/java/dev/jboehm/vectorvio/SimulationModel.java \
  app/src/main/java/dev/jboehm/vectorvio/CorridorRenderer.java \
  app/src/main/java/dev/jboehm/vectorvio/SyntheticVision.java \
  tools/simulation/SimulationBenchmark.java
java -Djava.awt.headless=true -cp "$classes" dev.jboehm.vectorvio.SimulationBenchmark "${1:-deliverables/simulation-final}" "${2:-3}" "${3:-default}" "${4:-15}" "${5:-1}"
