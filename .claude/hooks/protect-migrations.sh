#!/usr/bin/env bash
# PreToolUse (Edit|Write|MultiEdit): bloquea los cambios a migraciones de Flyway que ya existen en HEAD.
set -u
. "$(dirname "$0")/lib.sh"

payload=$(cat)
file=$(hook_file_path "$payload")
case "$file" in */db/migration/*) ;; *) exit 0 ;; esac

dir=$(dirname "$file")
[ -d "$dir" ] || exit 0

if git -C "$dir" cat-file -e "HEAD:./$(basename "$file")" 2>/dev/null; then
  echo "Bloqueado: $(basename "$file") es una migración Flyway ya commiteada y es inmutable (invariante 14 de AGENTS.md). Crea una migración nueva con la siguiente versión." >&2
  exit 2
fi
exit 0
