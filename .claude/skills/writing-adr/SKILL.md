---
name: writing-adr
description: Redactar un ADR en formato MADR para este repositorio (docs/adr/NNNN-*.md, en español). Úsalo al ejecutar /adr, cuando una decisión técnica tenga alternativas razonables, o antes de implementar algo que contradiga o reemplace un ADR existente.
---

# Escribir un ADR

Un ADR registra **por qué** se eligió algo cuando había alternativas reales. Si no hay alternativas razonables, no hace falta un ADR: basta con el código y la spec.

## Formato

Archivo `docs/adr/NNNN-<titulo-en-kebab-case>.md`, en español, con este orden:

```markdown
# NNNN · <Decisión en una línea, afirmativa>

- Estado: Propuesto | Aceptado | Reemplazado por NNNN
- Fecha: AAAA-MM-DD
- Requisitos: REQ-xx, ...

## Contexto y problema
## Factores de decisión
## Opciones consideradas
## Decisión
## Consecuencias
## Cómo se verifica
## Referencias
```

Después de crear el archivo, agrega la fila al índice `docs/adr/README.md`.

## Reglas de calidad

1. **El título es la decisión**, no el tema. "Kafka como sistema de mensajería, at-least-once con key = idPedido" sí; "Mensajería" no.
2. **El contexto nombra el problema concreto de este sistema** y los `REQ-xx` que lo originan. Nada de teoría genérica.
3. **Las opciones son reales**: al menos dos que alguien competente elegiría, cada una con su razón de descarte **en este contexto**.
4. **La decisión incluye los parámetros que la hacen verificable**: nombres de configuración, nombres de locks, tópicos, valores por defecto.
5. **Las consecuencias incluyen lo negativo y los riesgos**, con su mitigación. Un ADR sin consecuencias negativas está incompleto.
6. **"Cómo se verifica" apunta a tests o métricas concretas.** Si no se puede verificar, la decisión no está lo bastante definida.
7. **Un ADR aceptado no se reescribe.** Si cambia la decisión, se crea uno nuevo y se marca el anterior como `Reemplazado por NNNN`.
8. Se enlazan los ADRs relacionados con rutas relativas.

## Ejemplos de este repositorio

**Opción descartada con un escenario concreto de fallo** ([0003](../../../docs/adr/0003-outbox-con-polling-y-shedlock.md)):

> `SKIP LOCKED` reparte filas entre instancias sin mirar el agregado. Si `ReservarStock` (id 10) queda bloqueada por la instancia A y `LiberarStock` (id 11) del mismo pedido la toma la instancia B, B puede publicar primero. Kafka recibiría `LiberarStock` antes que `ReservarStock` en la misma partición y se rompería el orden que el resto del diseño asume.

Es bueno porque no dice "rompe el orden" en abstracto: muestra la secuencia exacta que lo rompe.

**Decisión explícita sobre los límites del mecanismo** ([0005](../../../docs/adr/0005-bloqueo-distribuido-como-defensa-en-profundidad.md)):

> Un lock distribuido sobre Redis **no es un mecanismo de corrección**. Si el lease vence durante una pausa de GC o una partición de red, dos procesos creen tener el lock (...)

y la tabla que fija, por cada lock, qué protege y qué pasa si no se obtiene.

**Consecuencia negativa asumida con su cota** ([0006](../../../docs/adr/0006-reintentos-bloqueantes-y-dlt.md)):

> **Bloqueo de cabeza de línea.** Un mensaje en reintento detiene los demás pedidos de su partición durante, como máximo, `max-elapsed-time`. Se acepta porque el tiempo es acotado y configurable, y la alternativa rompe la corrección.

## Antipatrones

- Un ADR que solo describe lo que hace el código.
- Opciones de paja ("no hacer nada") como única alternativa.
- Consecuencias solo positivas.
- Decidir versiones o valores sin verificarlos (ver la regla de versiones en `AGENTS.md`).
