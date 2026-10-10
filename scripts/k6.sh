#!/usr/bin/env bash
# Ejecuta la prueba de carga de sobreventa de k6 contra el stack levantado (primero scripts/up.sh).
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v k6 > /dev/null 2>&1; then
  echo "k6 no encontrado. Instálalo con: winget install k6 --source winget (Windows) o ver https://k6.io" >&2
  exit 1
fi
k6 run load/oversell.js "$@"
