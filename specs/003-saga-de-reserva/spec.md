# 003 · Saga de reserva

Estado: Aprobada
Requisitos: REQ-02, REQ-06, REQ-07, REQ-11, REQ-12, REQ-14, REQ-17, REQ-18, REQ-26, REQ-27

## Contexto

Crear un pedido dispara una saga orquestada por ms-pedidos ([ADR 0004](../../docs/adr/0004-saga-orquestada-dentro-de-ms-pedidos.md)):

1. ms-pedidos crea `Pedido(PENDING)` y escribe `ReservarStock` en su outbox (002).
2. El publisher del outbox lo envía a `inventario.comandos` con key = `idPedido`.
3. ms-inventario lo consume, deduplica, reserva todo o nada (001) y escribe `StockReservado` o `StockRechazado` en su outbox.
4. Su publisher lo envía a `inventario.eventos` con key = `idPedido`.
5. ms-pedidos lo consume, deduplica y transiciona a `CONFIRMED` o `REJECTED`.

Esta spec cubre el camino feliz y el de rechazo, el contrato de mensajes, el consumidor idempotente, el publisher del outbox, el timeout de la saga, los reintentos y la DLT. La cancelación y el orden de llegada están en la [004](../004-cancelacion-contra-reserva/spec.md).

## Contrato de mensajes (`contrato-eventos`)

Envelope común (record genérico):

| Campo | Tipo | Regla |
|---|---|---|
| `idMensaje` | UUID | Único por mensaje. Clave de deduplicación. |
| `tipo` | string | `ReservarStock`, `LiberarStock`, `StockReservado`, `StockRechazado`, `StockLiberado` |
| `versionEsquema` | int | `1` |
| `ocurridoEn` | instant | Momento del hecho de negocio |
| `idCorrelacion` | string | Se propaga sin cambios desde el request HTTP original |
| `idCausa` | UUID? | `idMensaje` del mensaje que causó este; nulo si lo causó un request HTTP o un job |
| `idAgregado` | UUID | `idPedido`. También es la key de Kafka. |
| `carga` | objeto | Según `tipo` |

| Tipo | Tópico | Carga |
|---|---|---|
| `ReservarStock` | `inventario.comandos` | `idPedido`, `lineas[{idProducto, cantidad}]` |
| `LiberarStock` | `inventario.comandos` | `idPedido`, `motivo`: `CANCELADO_POR_CLIENTE` · `TIMEOUT_SAGA` · `COMPENSACION_RESERVA_TARDIA` |
| `StockReservado` | `inventario.eventos` | `idPedido`, `lineas` |
| `StockRechazado` | `inventario.eventos` | `idPedido`, `motivo`: `STOCK_INSUFICIENTE` · `PRODUCTO_DESCONOCIDO` · `RESERVA_LIBERADA`, `idsProducto` |
| `StockLiberado` | `inventario.eventos` | `idPedido`, `desenlace`: `DEVUELTO` · `TOMBSTONE_REGISTRADO` · `NADA_QUE_LIBERAR` |

Se documenta en `docs/asyncapi.yaml` (Fase 6).

## Reglas de negocio

- **RN-01** Todo mensaje sale por el outbox, en la misma transacción que el cambio que lo origina (invariante 7), y con key = `idPedido` (invariante 9).
- **RN-02** Todo consumidor registra el `idMensaje` en `processed_messages` en la misma transacción que su efecto. Si el `idMensaje` ya existe, el mensaje es un duplicado: no hay efecto, se incrementa `messages_duplicated_total` y se hace ack (invariante 8).
- **RN-03** El offset se confirma después del commit de la base (invariante 10).
- **RN-04** **Todo comando recibe exactamente una respuesta por `idMensaje` distinto.** `ReservarStock` responde `StockReservado` o `StockRechazado`; `LiberarStock` responde `StockLiberado`. La respuesta lleva `idCausa` = `idMensaje` del comando.
- **RN-05** Order: `PENDING` + `StockReservado` → `CONFIRMED`. `PENDING` + `StockRechazado` → `REJECTED` con el motivo recibido.
- **RN-06** **Timeout de la saga.** Un pedido que lleva en `PENDING` más de `saga.timeout.umbral` pasa a `REJECTED` con motivo `TIMEOUT` y emite `LiberarStock(TIMEOUT_SAGA)`. Un job con ShedLock lo revisa cada `saga.timeout.intervalo-verificacion`.
- **RN-07** **Publisher del outbox.** Una sola instancia activa por servicio (ShedLock). Lee las filas no publicadas en orden de `id`, en lotes de `outbox.publicador.tamanio-lote`. Renueva el lock antes de cada envío; si la renovación falla, detiene el lote. El lease cubre la espera síncrona `max.block.ms`, `timeout-envio` y el marcado en la base. Marca cada fila como publicada solo después del ack del broker. Si el broker no responde, las filas quedan pendientes y se reintentan en el siguiente ciclo.
- **RN-08** **Reintentos** bloqueantes con backoff exponencial configurable. Los errores no reintentables van a `<topico>.DLT` con headers de causa. `messages_dlt_total{topic}` aumenta solo cuando el broker confirma la publicación; un envío fallido propaga el error para reintentar ([ADR 0006](../../docs/adr/0006-reintentos-bloqueantes-y-dlt.md), [ADR 0014](../../docs/adr/0014-renovacion-outbox-y-recuperacion-dlt.md)).
- **RN-09** Los topics tienen 3 particiones. Cada servicio corre con 2 réplicas en el mismo consumer group.

## Criterios de aceptación

### 003-AC-01 · Camino feliz de punta a punta (REQ-03, REQ-06, REQ-07, REQ-11)
- Given el producto A con `available = 5`
- When se crea un pedido A×2
- Then eventualmente el pedido queda `CONFIRMED` y A `available = 3, reserved = 2`

### 003-AC-02 · Rechazo por stock (REQ-07, REQ-11, REQ-13)
- Given A con `available = 1`
- When se crea un pedido A×2
- Then eventualmente el pedido queda `REJECTED` con `INSUFFICIENT_STOCK` y A no cambia

### 003-AC-03 · Rechazo por producto desconocido (REQ-07)
- When se crea un pedido con un `productId` inexistente
- Then eventualmente el pedido queda `REJECTED` con `UNKNOWN_PRODUCT`

### 003-AC-04 · `ReservarStock` duplicado (REQ-14, REQ-26)
- Given un `ReservarStock` ya procesado
- When llega de nuevo con el mismo `idMensaje`, dos y tres veces
- Then el stock y la reserva no cambian, hay una sola respuesta en el outbox y `messages_duplicated_total` aumenta por cada copia

### 003-AC-05 · Respuesta duplicada en Order (REQ-14, REQ-26)
- Given un `StockReservado` ya procesado y el pedido `CONFIRMED`
- When llega de nuevo con el mismo `idMensaje`
- Then el pedido no cambia (`version` incluida) y no se escribe nada en el outbox

### 003-AC-06 · Una respuesta por comando, con correlación (REQ-06, REQ-07)
- When se procesa cualquier comando
- Then se escribe exactamente una respuesta con `idCausa` = `idMensaje` del comando, el mismo `idCorrelacion` y el mismo `idAgregado`

### 003-AC-07 · Key y topic (REQ-17)
- When se publica cualquier mensaje
- Then la key de Kafka es `idPedido` y el topic corresponde al tipo según la tabla del contrato

### 003-AC-08 · Fallo antes del commit (REQ-26)
- Given el handler de `ReservarStock` falla después de descontar stock y antes del commit
- When el mensaje se reintenta
- Then no queda ningún efecto parcial y el reintento aplica la reserva exactamente una vez

### 003-AC-09 · Publisher del outbox (REQ-06, REQ-26)
- Given filas pendientes de dos pedidos, intercaladas
- When corren dos instancias del publisher
- Then cada fila se publica una vez y, para cada pedido, en orden de `id`
- And si el broker no está disponible, las filas quedan pendientes y se publican cuando vuelve

### 003-AC-10 · Timeout de la saga (REQ-07)
- Given un pedido en `PENDING` cuyo `saga_started_at` supera el umbral
- When corre el job de timeout
- Then el pedido queda `REJECTED` con `TIMEOUT` y existe `LiberarStock(TIMEOUT_SAGA)` en el outbox

### 003-AC-11 · Error temporal con reintento (REQ-18)
- Given el handler falla con un error reintentable dos veces y después funciona
- When se consume el mensaje, seguido de otro mensaje del mismo pedido
- Then el primero se aplica una vez y el segundo se procesa después del primero

### 003-AC-12 · Mensaje no procesable (REQ-18)
- Given un mensaje que no deserializa o no cumple el contrato
- When se consume
- Then va a `<topico>.DLT` sin reintentos, con headers de causa, y `messages_dlt_total{topic}` aumenta
- And los mensajes siguientes de la partición se procesan normalmente

### 003-AC-13 · Dos instancias por servicio (REQ-02)
- Given dos instancias de cada consumidor en el mismo group
- When se crean 50 pedidos sobre varios productos
- Then cada pedido llega a un estado terminal correcto y el stock cumple las invariantes 1 y 2

### 003-AC-14 · Renovación del lock del outbox (REQ-06, REQ-26)
- Given un lote cuya duración supera el lease inicial del publisher
- When otra instancia intenta adquirir el mismo lock durante el lote
- Then el publisher renueva el lock antes de cada envío y la otra instancia no lo adquiere
- And si una renovación falla, no se envía la siguiente fila y las restantes siguen pendientes

### 003-AC-15 · Publicación fallida en DLT (REQ-18)
- Given un mensaje no procesable y un broker que falla al publicar en la DLT
- When se intenta recuperarlo
- Then el contador de DLT no aumenta y el error se propaga para reintentar
- And cuando el broker confirma el envío, el contador aumenta una sola vez

### 003-AC-16 · Reproceso manual de una liberación (REQ-12, REQ-26, REQ-27)
- Given stock reservado y un `LiberarStock` que agotó sus reintentos y llegó a la DLT
- When se corrige la causa y se reenvía el mensaje original, conservando key y `idMensaje`
- Then el stock se libera y la reserva queda `RELEASED`
- And repetir ese reproceso no libera stock adicional

## Casos borde

- `versionEsquema` desconocida: no reintentable, va a la DLT.
- El job de timeout y un `StockReservado` llegan a la vez para el mismo pedido: está en la 004.
- Rebalanceo del consumer group durante el procesamiento: provoca reentregas, que cubre RN-02.

## Fuera de alcance

- Kafka transaccional / exactly-once ([ADR 0002](../../docs/adr/0002-kafka-como-sistema-de-mensajeria.md)).
- Reprocesamiento automático de la DLT (es manual y está documentado).
- Schema registry: el contrato se versiona con `versionEsquema`.

## Preguntas abiertas

- Ninguna.
