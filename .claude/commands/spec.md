---
description: Crea la spec, el plan y las tareas de una feature en specs/NNN-<feature>/
argument-hint: <feature-en-kebab-case>
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(ls:*), Bash(git status:*), Bash(git add:*), Bash(git commit:*)
---

Crea la spec de la feature `$ARGUMENTS` siguiendo SDD (Spec → Plan → Tasks → Implement).

## Pasos

1. Lee `docs/enunciado/requisitos.md`, `AGENTS.md` (invariantes) y las specs existentes en `specs/` para no duplicar ni contradecir.
2. Calcula `NNN` como el siguiente número libre de tres dígitos en `specs/`. Si ya existe una spec con el mismo nombre, detente y avisa.
3. Crea `specs/NNN-$ARGUMENTS/` con tres archivos en español:

### `spec.md`

```
# NNN · <Título>

Estado: Borrador | Aprobada
Requisitos: REQ-xx, REQ-yy

## Contexto
Por qué existe la feature y qué problema del enunciado resuelve.

## Reglas de negocio
RN-01 ... (numeradas, verificables)

## Criterios de aceptación
### NNN-AC-01 · <nombre>  (REQ-xx)
- Given ...
- When ...
- Then ...

## Casos borde
## Fuera de alcance
## Preguntas abiertas
```

### `plan.md`

Enfoque técnico, componentes por capa hexagonal (dominio / aplicacion / adaptadores), modelo de datos y migraciones, mensajes del contrato involucrados, ADRs relacionados (existentes o por escribir) y estrategia de tests (unitario / integración / concurrencia) por criterio.

### `tasks.md`

Checklist ordenada. Cada tarea es lo bastante pequeña para un ciclo red → green:

```
- [ ] T-01 · <comportamiento> · cubre NNN-AC-01 · nivel: unit | it | concurrency · módulo: <módulo>
```

4. Todo criterio de aceptación cita al menos un `REQ-xx` existente. Todo `REQ-xx` citado existe en `requisitos.md`.
5. Si la feature requiere una decisión con alternativas razonables y no hay un ADR, agrégalo en "Preguntas abiertas" y sugiere `/adr`.
6. Commit: `docs(specs): agrega el borrador de NNN-$ARGUMENTS`.
7. Muestra un resumen de reglas, criterios y tareas, y detente para revisión.
