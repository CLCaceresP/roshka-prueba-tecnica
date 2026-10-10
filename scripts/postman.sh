#!/usr/bin/env bash
# Ejecuta la colección de Postman con Newman contra el stack levantado (primero scripts/up.sh).
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v npx > /dev/null 2>&1; then
  echo "npx no encontrado. Instala Node.js 22 o superior desde https://nodejs.org" >&2
  exit 1
fi
npx --yes newman run postman/pedidos-inventario.postman_collection.json \
  -e postman/local.postman_environment.json "$@"
