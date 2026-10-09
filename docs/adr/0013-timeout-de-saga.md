# 0013 · Timeout de saga: job con ShedLock que rechaza y libera

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-02, REQ-07, REQ-27

## Contexto y problema

Si inventory nunca responde (su respuesta quedó en la DLT o hubo un bug), el pedido quedaría en `PENDING` para siempre, lo que viola la condición C-1 de la spec 004: todo pedido llega a un estado terminal. Además, una respuesta puede llegar **después** de que se dio el pedido por perdido.

## Factores de decisión

- Todo pedido termina en un estado terminal, en un tiempo acotado y configurable.
- Un `StockReservado` tardío no puede dejar stock retenido para un pedido rechazado.
- Funcionar con varias instancias sin procesar el mismo pedido dos veces.

## Opciones consideradas

1. **Sin timeout.** Simple, pero viola C-1.
2. **Mensaje diferido por pedido** (scheduler externo o topic con retardo). Kafka no tiene entrega diferida nativa; habría que agregar infraestructura.
3. **Job periódico con ShedLock** que busca pedidos `PENDING` con `saga_started_at` anterior al umbral.

## Decisión

**Opción 3.**

- Un job cada `saga.timeout.intervalo-verificacion` (ShedLock `saga-timeout`, una sola instancia a la vez) toma hasta `saga.timeout.tamanio-lote` pedidos `PENDING` con `saga_started_at < now - saga.timeout.umbral`.
- Cada pedido se procesa como cualquier otra entrada de la saga: lock `order:{id}`, transacción, `EstadoPedido.alTimeout()`. Si el pedido cambió entretanto (llegó la respuesta), la transición no tiene efecto.
- El timeout pasa el pedido a `REJECTED` con motivo `TIMEOUT` y emite `LiberarStock(TIMEOUT_SAGA)`.
- **Es seguro gracias al tombstone** ([0010](0010-consumidor-idempotente-y-tombstone.md)):
  - si inventory había reservado, el `LiberarStock` devuelve el stock;
  - si todavía no recibió el `ReservarStock`, el `LiberarStock` llega detrás en la misma partición y lo libera;
  - si el `ReservarStock` quedó en la DLT y se reprocesa más tarde, el tombstone lo rechaza.
- Un `StockReservado` tardío sobre `REJECTED` no cambia el estado y emite una compensación (spec 004, tabla Order).

## Consecuencias

- Positivas: C-1 garantizada; ningún pedido retiene stock indefinidamente.
- Negativas: un umbral demasiado bajo rechaza pedidos que se habrían confirmado. El valor por defecto (30 s) es varias veces la latencia normal de la saga, y se ajusta por configuración.

## Cómo se verifica

- Unitario: `alTimeout` en cada estado.
- IT: un pedido `PENDING` vencido → `REJECTED(TIMEOUT)` + `LiberarStock` en el outbox.
- Concurrencia: timeout y `StockReservado` simultáneos → `CONFIRMED` con reserva, o `REJECTED(TIMEOUT)` con liberación, nunca una mezcla (004-AC-07).
