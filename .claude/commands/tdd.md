---
description: Escribe SOLO el test rojo de una tarea, confirma que falla por la razón correcta y commitea (red)
argument-hint: <NNN/T-xx>
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(./mvnw:*), Bash(git status:*), Bash(git diff:*), Bash(git add:*), Bash(git commit:*)
---

Fase **red** de la tarea `$ARGUMENTS`.

1. Ubica la tarea en `specs/<NNN>-*/tasks.md` y el criterio de aceptación que cubre en `spec.md`. Si la tarea no existe o es ambigua, detente y pregunta.
2. Escribe **solo** el test, siguiendo `.claude/rules/testing.md`:
   - nombre `debe_<resultado>_cuando_<condicion>`;
   - `@DisplayName` con el `REQ-xx` y el `NNN-AC-xx` que cubre;
   - nivel indicado en la tarea (unit `*Test`, integración `*IT`, concurrencia `*ConcurrenciaIT` + `@Tag("concurrency")`).
3. Si hace falta para compilar, crea los tipos de producción **vacíos**: firmas mínimas que lanzan `UnsupportedOperationException` o devuelven un valor neutro. Nada de lógica.
4. Ejecuta el test:
   - unit: `./mvnw -q -pl <módulo> test -Dtest=<Clase>`
   - integración/concurrencia: `./mvnw -q -pl <módulo> verify -Dit.test=<Clase> -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`
5. Confirma que **falla por la razón correcta**: una aserción de comportamiento o la `UnsupportedOperationException` del stub. Si falla por compilación, configuración o infraestructura, corrígelo y repite. Si pasa, el test no prueba nada nuevo: detente y explica por qué.
6. Marca la tarea como `- [~]` (en rojo) en `tasks.md`.
7. Commit (solo el test, los stubs y `tasks.md`): `test(<scope>): <comportamiento> (red)`.
8. Muestra la salida relevante del fallo y **detente**. No implementes.
