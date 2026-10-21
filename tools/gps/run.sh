#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT
java com.sun.tools.javac.Main -d "$build_dir" app/src/main/java/dev/jboehm/vectorvio/gps/{Geo,GpsRoute,RouteMatcher,GpsNavigator,GpsFixRules}.java tools/gps/GpsBenchmark.java
java -cp "$build_dir" dev.jboehm.vectorvio.gps.GpsBenchmark "$@"
