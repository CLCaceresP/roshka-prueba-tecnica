#!/usr/bin/env bash
# Compila los jars y levanta el stack. Uso: scripts/up.sh
set -euo pipefail
cd "$(dirname "$0")/.."

./mvnw -B -q -ntp -DskipTests package

docker compose --profile full up -d --build --wait
docker compose --profile full ps
