# 0022 · Ajuste de stock con PUT absoluto e historial de movimientos

- Estado: Aceptado
- Fecha: 2026-10-10
- Requisitos: EXT-03, REQ-10, REQ-13, REQ-24, REQ-31

## Contexto y problema

Hay que poder reponer o retirar stock de un producto existente sin romper las invariantes: `available` y `reserved` nunca negativos y `available + reserved = total`. El cambio convive con reservas y liberaciones concurrentes y debe ser idempotente. Además, el verbo HTTP debe corresponder a la operación: lo que reemplaza el estado es un `PUT`.

## Factores de decisión

- La corrección bajo concurrencia debe salir de SQL, no de un lock distribuido (ADR 0009).
- Un reintento no puede duplicar el efecto (invariante 12).
- Un `PUT` describe el estado final del recurso y es idempotente por definición.
- Cada ajuste debe quedar en un historial de movimientos; no hay estado habilitado/deshabilitado de producto.

## Opciones consideradas

1. **`PUT /products/{id}/stock` con `available` absoluto (elegida).** El cuerpo dice cuántas unidades hay disponibles; `reserved` no cambia y `total = available + reserved`. La conservación se cumple por construcción.
2. **`POST` con un delta (`ADD`/`REMOVE`).** Fue la primera versión. Evita pisar cambios concurrentes, pero un delta no es idempotente por naturaleza y no es un `PUT`.
3. **`PUT` con el `total` absoluto.** Obliga a calcular `available = total - reserved` y puede dejarlo negativo si hay reservas vivas.
4. **Sin historial.** Más simple, pero no se sabe quién cambió el stock ni cuándo.

## Decisión

- `PUT /products/{id}/stock` con `available` (0 a 10.000.000) y `reason` opcional. Exige `Idempotency-Key`; la respuesta se guarda con el mecanismo del ADR 0011.
- Una sola sentencia SQL lee el valor anterior bajo `FOR UPDATE` y escribe `available = :n, total = :n + reserved` si `:n + reserved <= 10.000.000`. Así la fila queda serializada con las reservas y el movimiento registra la diferencia exacta.
- Si `:n + reserved` supera el tope, `409 STOCK_LIMIT_EXCEEDED`. Bajar `available` nunca falla: las unidades reservadas no se tocan.
- Si no hay cambio, `200` sin movimiento.
- Tabla `stock_movements` (migración `V5`, ya existente): `type` (`ADD` si subió, `REMOVE` si bajó), `quantity` (la diferencia), `total_after`, `available_after`. El movimiento se inserta en la misma transacción que el `UPDATE`.
- `GET /products/{id}/stock/movements` lista el historial paginado (ADR 0021).
- Se aclara la invariante 2: `total` es el stock cargado, incluidos los cambios hechos con este endpoint.

## Consecuencias

**Positivas**
- El verbo coincide con la semántica: repetir el `PUT` es seguro por definición, y la `Idempotency-Key` además reproduce la misma respuesta y el mismo movimiento.
- La conservación y el no-negativo los impone la base de datos, también bajo concurrencia. Toca una sola fila, así que no hay deadlocks.
- Cada cambio queda auditado.

**Negativas y riesgos**
- Un `PUT` pisa lo que otra operación haya dejado en `available`: dos operadores que ajustan a la vez se pisan (gana el último). Es lo que significa «fijar el valor»; el delta lo evitaba.
- Para reponer 10 unidades hay que conocer el valor actual (`GET .../stock`), calcular y enviarlo.
- Un cambio entre el `GET` y el `PUT` de un operador (por ejemplo, una reserva que baja `available`) hace que el `PUT` deje un valor distinto del que el operador esperaba.
- El historial crece sin límite; la retención queda fuera de alcance.
- El stock inicial no aparece en el historial.

## Cómo se verifica
- Spec 014: tests unitarios, de integración y de concurrencia (50 `PUT` simultáneos, misma key repetida, `PUT` contra reservas).

## Referencias
- [Spec 014](../../specs/014-ajuste-de-stock/spec.md) · [ADR 0009](0009-anti-sobreventa-en-sql.md) · [ADR 0011](0011-idempotencia-http.md)
