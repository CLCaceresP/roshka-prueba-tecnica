#!/usr/bin/env bash
# Utilidades compartidas por los hooks de Claude Code. Solo dependen de herramientas incluidas en Git for Windows (sin jq).

# Imprime el tool_input.file_path del payload del hook recibido por stdin, como ruta POSIX.
hook_file_path() {
  local payload="$1" path
  path=$(printf '%s' "$payload" \
    | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' \
    | head -n 1 \
    | sed -E 's/^"file_path"[[:space:]]*:[[:space:]]*"(.*)"$/\1/; s/\\\\/\\/g')
  [ -z "$path" ] && return 0
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -u "$path"
  else
    printf '%s\n' "$path"
  fi
}

project_root() {
  local root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -u "$root"
  else
    printf '%s\n' "$root"
  fi
}
