#!/usr/bin/env bash
# Ejecuta los tests unitarios, de arquitectura, de integración y de concurrencia (requiere Docker para Testcontainers).
# Los argumentos extra van a Maven, p. ej. scripts/test.sh -Dgroups=concurrency
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B -ntp verify "$@"
