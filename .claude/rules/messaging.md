---
paths:
  - "**/src/main/java/**"
  - "contrato-eventos/**"
  - "docs/asyncapi.yaml"
---

# Mensajería

Kafka at-least-once. Todo mensaje puede llegar dos o más veces, y un mensaje puede llegar tarde (después de un timeout o una cancelación).

## Publicar

- **Todo publish pasa por el outbox.** El caso de uso guarda el cambio de negocio y la fila del outbox en la **misma transacción**. Solo el publisher del outbox (`adaptadores/salida`) usa `KafkaTemplate`.
- **Key = `idAgregado` (`idPedido`)** siempre. Es lo que garantiza el orden por pedido dentro de una partición.
- Un único publisher activo por servicio (ShedLock). Renueva su lock antes de cada envío y detiene el lote si falla la renovación. Publica en orden de inserción del outbox y marca la fila como publicada solo tras el ack del broker. El lease cubre `max.block.ms`, `timeout-envio` y el marcado en la base.
- Producer con `enable.idempotence=true` y `acks=all`.

## Consumir

- **Todo consumidor es idempotente.** Inserta `idMensaje` en `processed_messages` dentro de la misma transacción que el efecto. Una violación de PK significa duplicado: se ignora, se incrementa `messages_duplicated_total` y se hace ack.
- **Además, el efecto es idempotente por estado:** reservas por `idPedido` y transiciones de `Pedido` que toleran eventos tardíos. El reproceso de la DLT conserva el `idMensaje` original. Deduplicar por `idMensaje` no basta, porque comandos nuevos para el mismo pedido, como una compensación, tienen otros `idMensaje`.
- El offset se confirma **después** del commit de la base de datos (ack manual).
- Los reintentos son **bloqueantes** (`DefaultErrorHandler` + `ExponentialBackOff`) para no romper el orden de la partición. Nunca `@RetryableTopic`.
- Las excepciones no reintentables (deserialización, validación, transición inválida) van directo a `<topico>.DLT` con headers de causa. Solo un envío confirmado por el broker incrementa `messages_dlt_total{topic}` y deja el log de publicación exitosa. Un fallo se propaga para reintentar. Recuperación manual: `docs/dlt-recovery.md`.

## Contrato

- Sobre (`SobreMensaje`): `idMensaje`, `tipo`, `versionEsquema`, `ocurridoEn`, `idCorrelacion`, `idCausa`, `idAgregado`, `carga`. El JSON de los eventos usa estos nombres en español.
- `idCausa` = `idMensaje` del mensaje que causó este. `idCorrelacion` se propaga sin cambios a lo largo de toda la saga.
- Los cambios de contrato son aditivos. Un cambio incompatible sube `versionEsquema` y se documenta en `docs/asyncapi.yaml`.
- `contrato-eventos` solo contiene records. Nada de lógica, Spring ni Jackson más allá de anotaciones imprescindibles.
- Los valores de `tipo` son los nombres de los records en español (`ReservarStock`, `StockReservado`).

## Tópicos

| Tópico | Productor | Consumidor | Mensajes |
|---|---|---|---|
| `inventario.comandos` | ms-pedidos | ms-inventario | `ReservarStock`, `LiberarStock` |
| `inventario.eventos` | ms-inventario | ms-pedidos | `StockReservado`, `StockRechazado`, `StockLiberado` |
| `<topico>.DLT` | error handler | operación manual | mensaje original + headers de causa |
