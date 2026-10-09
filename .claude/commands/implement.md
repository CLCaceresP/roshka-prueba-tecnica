---
description: Implementación mínima para pasar a verde la tarea en rojo y commit (green)
argument-hint: <NNN/T-xx>
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(./mvnw:*), Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git add:*), Bash(git commit:*)
---

Fase **green** de la tarea `$ARGUMENTS`.

1. Verifica en `git log` que existe el commit `(red)` de esta tarea y que la tarea está marcada `- [~]` en `tasks.md`. Si no, detente: primero va `/tdd`.
2. Relee el test, la spec y las reglas aplicables (`architecture.md`, `messaging.md`, `java-style.md`) y las invariantes de `AGENTS.md`.
3. Implementa **lo mínimo** para que el test pase, respetando las capas hexagonales. Nada de funcionalidad que el test no exija.
4. No modifiques el test para que pase. Si el test está mal, detente y explícalo.
5. Si aparece un cambio de esquema, crea una migración Flyway **nueva**; nunca edites una ya commiteada.
6. Ejecuta:
   - el test de la tarea;
   - la suite del módulo: `./mvnw -q -pl <módulo> verify`;
   - `./mvnw -q spotless:check`.
7. Marca la tarea como `- [x]` en `tasks.md`.
8. Commit: `feat(<scope>): <comportamiento> (green)` (o `fix(...)` si corresponde).
9. Si ves un refactor claro, propónlo sin aplicarlo. Va en un commit `refactor(<scope>): ...` aparte, con los tests en verde.
10. Resume qué cambió y qué invariantes toca. Sugiere `/review` si se tocaron mensajería, transacciones o locks.
