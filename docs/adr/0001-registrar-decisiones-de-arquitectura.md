# 0001 · Registrar decisiones de arquitectura con MADR

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: BON-02, BON-04

## Contexto y problema

La prueba pide justificar decisiones (por ejemplo, la elección de mensajería en REQ-18) y valora la arquitectura y la configuración de agentes. Varias decisiones de este sistema tienen alternativas razonables y consecuencias no obvias: orden de mensajes, garantías de entrega, locks. Sin un registro, el evaluador y los agentes solo verían el resultado, no el porqué, y un agente podría "simplificar" una decisión deliberada.

## Factores de decisión

- Debe poder leerse en el propio repositorio, sin herramientas externas.
- Debe tener el formato fijo que siguen los agentes (`/adr`, skill `writing-adr`).
- Cada decisión debe poder rastrearse hasta los requisitos.

## Opciones consideradas

1. **ADRs en formato MADR** dentro de `docs/adr/`.
2. Una sección "Decisiones" en el README.
3. Wiki o documento externo.

## Decisión

Opción 1. Un archivo por decisión, numerado con cuatro dígitos y escrito en español, con las secciones de MADR: contexto, factores, opciones, decisión, consecuencias, verificación y referencias. Un ADR aceptado no se edita en su sustancia; si la decisión cambia, otro ADR lo reemplaza y ambos se enlazan. El índice vive en `docs/adr/README.md`.

## Consecuencias

- Positivas: el razonamiento queda versionado junto al código y los revisores y agentes pueden citar el ADR.
- Negativas: mantenerlos tiene un costo. Se mitiga escribiendo un ADR solo cuando hay alternativas reales.

## Cómo se verifica

`/review` y `architecture-reviewer` señalan los cambios que contradicen un ADR aceptado.

## Referencias

- MADR: https://adr.github.io/madr/
