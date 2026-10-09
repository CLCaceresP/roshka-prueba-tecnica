---
description: Crea un ADR numerado en docs/adr/ con formato MADR
argument-hint: <título de la decisión>
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(ls:*), Bash(git add:*), Bash(git commit:*)
---

Crea un ADR para: **$ARGUMENTS**.

Sigue el skill `writing-adr` (`.claude/skills/writing-adr/SKILL.md`). En resumen:

1. Calcula el siguiente número libre de cuatro dígitos en `docs/adr/`.
2. Archivo: `docs/adr/NNNN-<titulo-en-kebab-case>.md`, en español, formato MADR:
   - Estado (`Propuesto` hasta que el usuario lo apruebe), fecha y requisitos relacionados;
   - Contexto y problema;
   - Factores de decisión;
   - Opciones consideradas (al menos dos reales);
   - Decisión y justificación;
   - Consecuencias (positivas, negativas y riesgos);
   - Cómo se verifica (test, métrica o revisión);
   - Referencias.
3. Si reemplaza a otro ADR, marca el anterior como `Reemplazado por NNNN` y enlázalos.
4. Agrega la fila al índice `docs/adr/README.md`.
5. Commit: `docs(adr): NNNN <título corto>`.
6. Muestra la decisión y las consecuencias en cinco líneas y detente.
