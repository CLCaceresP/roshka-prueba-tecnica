#!/usr/bin/env bash
# Baja el stack y borra sus volúmenes.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose --profile full down -v --remove-orphans
