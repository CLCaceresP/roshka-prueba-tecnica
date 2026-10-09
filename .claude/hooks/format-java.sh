#!/usr/bin/env bash
# PostToolUse (Edit|Write|MultiEdit): formatea con Spotless el .java editado, acotado a su módulo Maven.
# No hace nada mientras el build no tenga Maven Wrapper ni el plugin de Spotless.
set -u
. "$(dirname "$0")/lib.sh"

payload=$(cat)
file=$(hook_file_path "$payload")
case "$file" in *.java) ;; *) exit 0 ;; esac
[ -f "$file" ] || exit 0

root=$(project_root)
[ -f "$root/mvnw" ] && [ -f "$root/pom.xml" ] || exit 0
grep -q 'spotless-maven-plugin' "$root/pom.xml" || exit 0

module_dir=$(dirname "$file")
while [ "$module_dir" != "$root" ] && [ "$module_dir" != "/" ] && [ ! -f "$module_dir/pom.xml" ]; do
  module_dir=$(dirname "$module_dir")
done
module=${module_dir#"$root"}
module=${module#/}

name=$(basename "$file")
args=(-q spotless:apply "-DspotlessFiles=.*[/\\\\]${name//./\\.}")
[ -n "$module" ] && args=(-pl "$module" "${args[@]}")

if ! output=$(cd "$root" && sh ./mvnw "${args[@]}" 2>&1); then
  echo "Spotless no pudo formatear $name (¿error de sintaxis?):" >&2
  printf '%s\n' "$output" | tail -n 20 >&2
  exit 2
fi
exit 0
