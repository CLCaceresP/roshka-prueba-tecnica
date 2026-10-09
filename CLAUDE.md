@AGENTS.md

# Específico de Claude Code

## Flujo de trabajo por fases

El trabajo se divide en fases (0 a 6). Al terminar una fase, todo compila y todos los tests pasan. El alcance es el del plan: no se agregan componentes sin acordarlo.

Fases: 0 fundaciones · 1 esqueleto · 2 inventario · 3 pedidos y saga · 4 carrera y resiliencia · 5 gateway y observabilidad · 6 entregables.

El responsable del proyecto toma las decisiones finales. Si una instrucción contradice el enunciado o es técnicamente incorrecta, se señala en vez de ejecutarla.

## Ciclo por feature

1. `/spec <feature>`: spec, plan y tareas en `specs/NNN-<feature>/`.
2. `/adr <título>`: si la feature implica una decisión con alternativas, el ADR va antes del código.
3. `/tdd <tarea>`: solo el test rojo, se ejecuta, se confirma que falla por la razón correcta, commit `(red)` y alto.
4. `/implement <tarea>`: lo mínimo para pasar a verde y commit `(green)`. Refactor opcional en un commit aparte.
5. `/review`: los tres subagentes revisores analizan el diff.
6. `/concurrency-check`: después de tocar reserva, cancelación, idempotencia o consumidores.

## Subagentes

Viven en `.claude/agents/`, tienen herramientas de solo lectura y reciben el diff ya calculado.

- `idempotency-reviewer`: dual-writes, consumidores no idempotentes, efectos fuera de transacción, keys de partición incorrectas.
- `architecture-reviewer`: violaciones de capas, lógica en controllers, dominio acoplado a frameworks.
- `test-reviewer`: tests que no prueban comportamiento, mocks innecesarios, sleeps, aserciones débiles.

## Reglas por área

`.claude/rules/` se carga según la ruta del archivo que se toca: `java-style.md`, `testing.md`, `messaging.md`, `architecture.md`.

## Hooks activos (`.claude/settings.json`)

- Después de editar un `.java`, Spotless formatea ese archivo. No hace nada mientras no existan `mvnw` y `pom.xml`.
- Se bloquea la edición de migraciones de Flyway que ya están en `HEAD`. Para cambiar el esquema se crea una migración nueva.
- Se deniegan `git push --force` y los borrados recursivos.

## Windows

- Los comandos corren en Git Bash: rutas con `/` y `./mvnw`, nunca `mvn` global.
- Las rutas pueden tener espacios: siempre se citan.
- Nunca se hace `git push`. El remoto lo gestiona el responsable del proyecto.
