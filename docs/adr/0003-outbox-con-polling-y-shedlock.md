# 0003 · Transactional Outbox con publisher por polling y ShedLock

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-02, REQ-06, REQ-07, REQ-26, REQ-27

## Contexto y problema

Cada caso de uso cambia el estado en la base y debe emitir un mensaje: crear un pedido emite `ReservarStock` y reservar stock emite `StockReservado`. Escribir en Postgres y publicar en Kafka son dos sistemas sin transacción común (dual-write):

- Si se publica antes del commit y la transacción hace rollback, sale un mensaje fantasma.
- Si se publica después del commit y el proceso cae, el mensaje se pierde y el pedido queda en `PENDING` para siempre.

Además, los mensajes de un mismo pedido deben salir **en el orden en que se generaron** ([0002](0002-kafka-como-sistema-de-mensajeria.md)).

## Factores de decisión

- No perder mensajes ni emitir mensajes de transacciones revertidas.
- Preservar el orden por agregado (`orderId`).
- Funcionar con dos réplicas de cada servicio.
- Simplicidad operativa en compose.

## Opciones consideradas

1. **Publicar directo después del commit** (`TransactionSynchronization.afterCommit`). Simple, pero pierde mensajes si el proceso cae justo después del commit.
2. **Outbox con un solo publisher activo por polling, coordinado con ShedLock.**
3. **Outbox con publishers en paralelo usando `SELECT ... FOR UPDATE SKIP LOCKED`.**
4. **Outbox con CDC (Debezium)** leyendo el WAL de Postgres.
5. **Transacción "encadenada" DB + Kafka** (best-effort 1PC). Spring la deprecó y no es atómica ante fallos entre los dos commits.

## Decisión

**Opción 2.** El caso de uso inserta la fila del outbox en la **misma transacción** que el cambio de negocio (invariante 7). Un job programado publica las filas pendientes **en orden de `id`**, espera el ack del broker y solo entonces las marca como publicadas. ShedLock garantiza que **una sola instancia** ejecute el job a la vez.

### Por qué no `SKIP LOCKED` con publishers en paralelo

`SKIP LOCKED` reparte filas entre instancias sin mirar el agregado. Si `ReservarStock` (id 10) queda bloqueada por la instancia A y `LiberarStock` (id 11) del mismo pedido la toma la instancia B, B puede publicar primero. Kafka recibiría `LiberarStock` antes que `ReservarStock` en la misma partición y se rompería el orden que el resto del diseño asume. Se podría particionar el outbox por `hash(orderId)` con un lock por partición, pero eso es complejidad que este volumen no justifica.

### Por qué en producción se usaría CDC (Debezium)

Debezium lee el WAL en orden de commit, sin polling, con menor latencia y sin carga de consultas sobre la tabla. Elimina el job, ShedLock y el marcado de filas. Requiere Kafka Connect, replicación lógica en Postgres y operar un componente más, desproporcionado para la prueba. Queda como evolución posible.

### Regla de orden por agregado

Un único publisher que lee en orden de `id` no basta por sí solo. Las secuencias de Postgres asignan ids en orden de **inserción**, no de **commit**: si dos transacciones del mismo pedido se solaparan, la de id mayor podría commitear primero. Para impedirlo:

- **Toda transacción que inserta en el outbox fuerza el incremento de versión del agregado** al que pertenece el mensaje (`Pedido` con `@Version` y `OPTIMISTIC_FORCE_INCREMENT`; en inventory, la fila de `reservations` del pedido). Dos transacciones solapadas del mismo agregado no pueden commitear ambas: la segunda falla por versión y se reintenta.
- Así, para un mismo agregado, orden de `id` = orden de commit. Entre agregados distintos el orden de publicación no importa.
- El publisher selecciona por estado (`published_at IS NULL ORDER BY id`), no con una marca de agua sobre `id`, para no saltarse filas de transacciones lentas.

## Consecuencias

- Positivas: ningún mensaje se pierde ni se emite desde un rollback; orden por pedido garantizado; se puede observar con `outbox_pending_messages` y `outbox_publish_lag_seconds`.
- Negativas: latencia adicional igual al intervalo de polling (configurable, `outbox.publicador.intervalo-sondeo`). El throughput de publicación queda limitado a una instancia por servicio, suficiente para la prueba.
- Riesgos:
  - Si el publisher cae después del ack de Kafka y antes de marcar la fila, el mensaje se publica dos veces. Lo cubren los consumidores idempotentes (invariante 8).
  - Si la instancia dueña del lock muere, otra lo toma cuando vence `lockAtMostFor`. Desde [0014](0014-renovacion-outbox-y-recuperacion-dlt.md), el lock se renueva antes de cada envío: el lease debe cubrir una fila, incluyendo `max.block.ms`, `send-timeout` y el marcado en la base.
  - La tabla crece: las filas publicadas se purgan con un job configurable.

## Cómo se verifica

- Test de integración: un rollback del caso de uso no deja una fila en el outbox.
- Test de integración: dos instancias del publisher sobre la misma base publican cada fila una sola vez y en orden de `id` por agregado.
- Test: la escritura en el outbox incrementa la versión del agregado.

## Referencias

- Chris Richardson, *Microservices Patterns*, "Transactional outbox".
- ShedLock: https://github.com/lukas-krecan/ShedLock
- Debezium Outbox Event Router.
