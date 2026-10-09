#!/usr/bin/env bash
# PreToolUse (Bash|PowerShell): segunda barrera tras las reglas deny de settings.json, para órdenes de flags
# que las reglas de permiso por prefijo no pueden reconocer (p. ej. "git push origin main --force").
set -u

payload=$(cat)

if printf '%s' "$payload" | grep -Eq 'git[[:space:]]+push([^"]*[[:space:]])?(--force|--force-with-lease|-f)([[:space:]=]|"|$)'; then
  echo "Bloqueado: git push forzado está prohibido en este repositorio." >&2
  exit 2
fi

if printf '%s' "$payload" | grep -Eq '(^|[^[:alnum:]_-])rm[[:space:]]+(-[[:alpha:]]*[rR][[:alpha:]]*|--recursive)([[:space:]]|"|$)'; then
  echo "Bloqueado: los borrados recursivos (rm -r/-rf) están prohibidos. Borra archivos concretos o pide confirmación al usuario." >&2
  exit 2
fi

if printf '%s' "$payload" | grep -Eiq '(^|[^[:alnum:]_-])(remove-item|ri|rmdir|rd|del|erase|rm)[[:space:]][^"|;]*-recurse'; then
  echo "Bloqueado: los borrados recursivos (Remove-Item -Recurse) están prohibidos." >&2
  exit 2
fi
exit 0
