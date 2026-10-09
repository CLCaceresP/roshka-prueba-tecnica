---
description: Revisa el diff actual con los subagentes idempotency, architecture y test reviewer
argument-hint: "[rango git opcional, p. ej. main..HEAD]"
allowed-tools: Read, Glob, Grep, Bash(git diff:*), Bash(git status:*), Bash(git log:*), Bash(git show:*), Agent
---

Revisión del cambio actual.

1. Determina el diff:
   - si se recibió `$ARGUMENTS`, usa `git diff $ARGUMENTS`;
   - si no, `git diff HEAD` (staged + unstaged) más la lista de archivos sin seguimiento de `git status --porcelain`;
   - si está vacío, usa `git show HEAD`.
2. Lanza **en paralelo** los tres subagentes. A cada uno pásale el diff completo y la lista de archivos tocados:
   - `idempotency-reviewer`
   - `architecture-reviewer`
   - `test-reviewer`
3. Consolida los hallazgos en una tabla única, sin duplicados, ordenada por severidad:

| Severidad | Revisor | Archivo:línea | Hallazgo | Invariante/regla | Sugerencia |
|---|---|---|---|---|---|

   Severidades: **bloqueante** (rompe una invariante de `AGENTS.md` o una regla), **importante** (riesgo real con un escenario concreto), **menor** (calidad).
4. Descarta los hallazgos sin un escenario concreto de fallo.
5. **No apliques correcciones.** Presenta la tabla y espera la decisión del usuario.
