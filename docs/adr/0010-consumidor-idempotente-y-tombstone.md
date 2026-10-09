# 0010 · Consumidor idempotente y la reserva como punto de serialización, con tombstone

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-12, REQ-14, REQ-15, REQ-26, REQ-27

## Contexto y problema

Con at-least-once (REQ-26), inventory recibe:

- **duplicados exactos:** el mismo `idMensaje`, por reentrega o por doble publicación del outbox;
- **duplicados lógicos:** el mismo pedido con otro `idMensaje`, como la cancelación seguida de la compensación, o el replay desde la DLT;
- **mensajes fuera de orden:** `LiberarStock` antes que `ReservarStock`, solo posible fuera de la operación normal (spec 004, RN-12).

Además, durante un rebalanceo, dos instancias pueden procesar a la vez mensajes del mismo pedido.

## Factores de decisión

- Que cada mensaje tenga efecto exactamente una vez, aunque se entregue varias veces.
- El stock de una reserva se devuelve como máximo una vez.
- Serializar el trabajo de un mismo pedido sin depender del lock distribuido.

## Opciones consideradas

1. **Solo deduplicar por `idMensaje`.** Cubre los duplicados exactos, pero no los lógicos: dos `LiberarStock` con distinto `idMensaje` devolverían el stock dos veces.
2. **Solo idempotencia por estado.** Cubre los lógicos, pero un duplicado exacto volvería a escribir una respuesta en el outbox. Es inocuo, pero genera ruido y no permite contar duplicados.
3. **Ambas:** `processed_messages` para los exactos y la fila de `reservations` como estado y punto de serialización para los lógicos.

## Decisión

**Opción 3.** En **una sola transacción**, en este orden:

1. `insert into processed_messages ... on conflict do nothing`. Si afecta 0 filas, es un duplicado exacto: no hay efecto y se incrementa `messages_duplicated_total`. Se usa `ON CONFLICT` en lugar de capturar la violación de PK porque en Postgres un error aborta la transacción.
2. `select ... from reservations where order_id = ? for update`.
   - Si la fila existe, el lock de fila serializa a cualquier otra transacción del mismo pedido.
   - Si no existe, se calcula el efecto (reserva en el ledger o tombstone) y se **inserta** la fila. Una segunda transacción concurrente del mismo pedido se bloquea en el insert por la PK. Cuando la primera commitea, la segunda falla por clave duplicada, hace rollback completo (incluido su descuento de stock) y se reintenta con backoff. En el reintento ya ve la fila y aplica la tabla de la spec 004.
3. Se escribe la respuesta en el outbox **después** de insertar o bloquear la fila. Así, para un mismo pedido, el orden de `id` del outbox coincide con el orden de commit. Es la regla de [0003](0003-outbox-con-polling-y-shedlock.md), cumplida aquí con el lock de fila en lugar de una versión.

**Tombstone:** un `LiberarStock` sin reserva previa inserta una fila `RELEASED` sin líneas y no toca el stock. Cualquier `ReservarStock` posterior del mismo pedido ve `RELEASED` y responde `StockRechazado(RESERVATION_RELEASED)` sin tocar el stock.

## Consecuencias

- Positivas: duplicados exactos y lógicos cubiertos; stock devuelto como máximo una vez (solo la transición `RESERVED → RELEASED` lo toca); correcto sin el lock distribuido.
- Negativas: `processed_messages` crece con cada mensaje. Se purga por antigüedad (más que la retención del topic) en una mejora documentada.
- Riesgos: la carrera de inserts concurrentes del mismo pedido produce una excepción de clave duplicada que se resuelve con reintento. El clasificador de errores debe tratarla como **reintentable** ([0006](0006-reintentos-bloqueantes-y-dlt.md)).

## Cómo se verifica

- IT: el mismo `ReservarStock` entregado tres veces → stock descontado una vez y una sola respuesta.
- IT: `LiberarStock` antes que `ReservarStock` → tombstone y rechazo sin tocar stock.
- Concurrencia: dos `LiberarStock` distintos a la vez → stock devuelto una vez.
