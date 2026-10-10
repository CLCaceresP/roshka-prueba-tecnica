#!/usr/bin/env bash
# Levanta solamente el front local; Ctrl+C lo cierra sin bajar Docker.
# Uso: scripts/front.sh [--puerto 5174] [--gateway http://localhost:8080]
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v node > /dev/null 2>&1; then
  echo "Se necesita Node.js 22 o superior para ejecutar el front." >&2
  exit 1
fi
node front/servidor.mjs "$@"
