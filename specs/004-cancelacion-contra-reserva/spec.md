# 004 · Carrera entre cancelación y reserva

Estado: Aprobada
Requisitos: REQ-05, REQ-09, REQ-12, REQ-14, REQ-15, REQ-21, REQ-25, REQ-26, REQ-27

## Contexto

El enunciado exige que un pedido pueda cancelarse **mientras la reserva de stock se está procesando** y que el estado final sea consistente (REQ-27). En un sistema asíncrono, at-least-once y con varias instancias, la cancelación, la respuesta de inventory, el timeout de la saga y los duplicados pueden llegar en cualquier orden. La consistencia no puede depender de que lleguen "en el orden esperado": cada servicio tiene que decidir correctamente **para cada combinación de estado actual y mensaje recibido**.

Esta spec define esas decisiones como dos tablas, una por servicio, y los órdenes de llegada que se demuestran con tests.

## Definición de "estado final consistente"

Cuando el sistema se estabiliza (outbox vacío y todos los mensajes consumidos), para todo pedido O:

- **C-1** O está en un estado terminal (`CONFIRMED`, `REJECTED` o `CANCELLED`). `PENDING` solo es transitorio, acotado por el timeout de la saga.
- **C-2** Si O está en `CONFIRMED`, su reserva está en `RESERVED`.
- **C-3** Si O está en `CANCELLED` o `REJECTED`, su reserva no existe, está en `REJECTED` o en `RELEASED`. **Nunca `RESERVED`.**
- **C-4** Para todo producto, `reserved` = suma de las cantidades de las reservas `RESERVED` que lo incluyen, y `available + reserved = total`.
- **C-5** Ningún pedido pasó por un estado no permitido por la máquina de estados (002).

Como `CONFIRMED` es el único estado que retiene stock, C-2, C-3 y C-4 implican que **el stock retenido corresponde exactamente a los pedidos confirmados**.

## Reglas de negocio

### Cancelación (HTTP)

- **RN-01** `DELETE /orders/{orderId}` requiere `Idempotency-Key` (005) y toma el lock `order:{orderId}` ([ADR 0005](../../docs/adr/0005-bloqueo-distribuido-como-defensa-en-profundidad.md)).
- **RN-02** Cancelar en `PENDING` o `CONFIRMED` pasa a `CANCELLED` y escribe `LiberarStock(CANCELADO_POR_CLIENTE)` en el outbox, en la misma transacción. Responde `200` con el pedido.
- **RN-03** Cancelar en `CANCELLED` responde `200` sin efectos: es idempotente por estado, aun con otra `Idempotency-Key`.
- **RN-04** Cancelar en `REJECTED` responde `409` `ORDER_NOT_CANCELLABLE`.
- **RN-05** Si no se obtiene el lock en `bloqueo.espera-bloqueo`, o hay un conflicto de versión, responde `409` `CONCURRENT_MODIFICATION` con `Retry-After`. No se aplica ningún efecto.
- **RN-06** Pedido inexistente: `404` `ORDER_NOT_FOUND`.

### Order: estado × entrada

Toda fila se ejecuta bajo el lock `order:{orderId}`, con `@Version` y con incremento forzado de versión cuando escribe en el outbox. Los duplicados por `idMensaje` se descartan antes de consultar esta tabla (003, RN-02).

| Estado actual | `cancel` (HTTP) | `StockReservado` | `StockRechazado` | `StockLiberado` | Timeout de saga |
|---|---|---|---|---|---|
| `PENDING` | → `CANCELLED` + `LiberarStock(CANCELADO_POR_CLIENTE)` · 200 | → `CONFIRMED` | → `REJECTED(motivo)` | sin cambio (registro) | → `REJECTED(TIMEOUT)` + `LiberarStock(TIMEOUT_SAGA)` |
| `CONFIRMED` | → `CANCELLED` + `LiberarStock(CANCELADO_POR_CLIENTE)` · 200 | sin cambio | **imposible** → DLT | sin cambio (registro) | no aplica (solo `PENDING`) |
| `CANCELLED` | sin cambio · 200 | sin cambio + `LiberarStock(COMPENSACION_RESERVA_TARDIA)` | sin cambio | sin cambio (registro) | no aplica |
| `REJECTED` | 409 `ORDER_NOT_CANCELLABLE` | sin cambio + `LiberarStock(COMPENSACION_RESERVA_TARDIA)` | sin cambio | sin cambio (registro) | no aplica |

Notas:

- **RN-07** Un evento tardío **nunca** saca a un pedido de un estado terminal (invariante 6).
- **RN-08** `StockReservado` sobre `CANCELLED` o `REJECTED` emite una compensación. Con el diseño actual es **redundante pero inocua**. El `LiberarStock` emitido al cancelar o al vencer el timeout ya viaja detrás de `ReservarStock` en la misma partición, así que inventory reserva y luego libera. La compensación adicional llega a una reserva `RELEASED` y no tiene efecto. Se mantiene como defensa por si el primer `LiberarStock` terminó en la DLT y se reprocesó en otro orden.
- **RN-09** `StockRechazado` sobre `CONFIRMED` es imposible: inventory solo rechaza una reserva que no existe o que ya fue liberada, y `CONFIRMED` implica `RESERVED`. Si ocurre, indica un bug o un contrato roto y va a la DLT como no reintentable ([ADR 0006](../../docs/adr/0006-reintentos-bloqueantes-y-dlt.md)).

### Inventory: estado de la reserva × comando

La fila de `reservations` del pedido es el **punto de serialización**: se inserta (la PK sobre `order_id` serializa a quien llega primero) o se lee con `SELECT ... FOR UPDATE`. Toda fila escribe exactamente una respuesta en el outbox (003, RN-04).

| Reserva actual | `ReservarStock` | `LiberarStock` |
|---|---|---|
| no existe | Reserva todo o nada (001). Éxito → `RESERVED` + `StockReservado`. Fallo → `REJECTED(motivo)` + `StockRechazado(motivo)`, sin tocar stock. | Crea un **tombstone** `RELEASED` sin líneas y sin tocar stock + `StockLiberado(TOMBSTONE_REGISTRADO)` |
| `RESERVED` | sin cambio de stock + `StockReservado` (reenvía el resultado registrado) | devuelve el stock de cada línea en orden ascendente de `productId` → `RELEASED` + `StockLiberado(DEVUELTO)` |
| `REJECTED` | sin cambio + `StockRechazado(motivo original)` | sin cambio + `StockLiberado(NADA_QUE_LIBERAR)` |
| `RELEASED` | sin cambio de stock + `StockRechazado(RESERVATION_RELEASED)` | sin cambio + `StockLiberado(NADA_QUE_LIBERAR)` |

- **RN-10** El stock de una reserva se devuelve como máximo una vez (invariante 5): solo la transición `RESERVED → RELEASED` toca stock.
- **RN-11** Un tombstone `RELEASED` bloquea para siempre cualquier `ReservarStock` posterior del mismo pedido.
- **RN-12** Con un único publisher de outbox, key = `idPedido` y reintentos bloqueantes, `LiberarStock` **no puede** llegar antes que `ReservarStock` en operación normal. El tombstone cubre el reprocesamiento desde la DLT, el replay manual y futuros cambios de infraestructura (por ejemplo, CDC). En los tests se inyecta directamente.

## Órdenes de llegada a demostrar

Notación: `R` = `ReservarStock`, `L` = `LiberarStock`, `SR` = `StockReservado`, `SJ` = `StockRechazado`, `C` = cancelación HTTP, `T` = timeout de saga. Stock inicial `total = available = 10` salvo indicación.

### 004-AC-01 · Cancelación antes de que inventory procese la reserva (REQ-27)
- Given un pedido O(A×3) en `PENDING`, con `R` aún sin consumir
- When llega `C`, y luego inventory consume `R` y `L` en orden
- Then O queda `CANCELLED`, la reserva `RELEASED` y A `available = 10, reserved = 0`

### 004-AC-02 · Cancelación concurrente con el manejo de `SR` (REQ-27, REQ-25)
- Given O(A×3) en `PENDING` y la reserva ya `RESERVED` en inventory
- When `C` y el handler de `SR` se ejecutan a la vez, en 2 instancias de order, arrancando juntos con un `CountDownLatch`, repetido 50 veces con pedidos distintos
- Then en cada repetición O termina `CANCELLED` (sea `PENDING → CANCELLED` o `PENDING → CONFIRMED → CANCELLED`), la reserva `RELEASED` y A recupera todo el stock
- And ninguna transacción deja una escritura perdida: `version` es coherente y no hay `CONFIRMED` sobre un `CANCELLED`

### 004-AC-03 · Cancelación después de `CONFIRMED` (REQ-05, REQ-12)
- Given O(A×3) `CONFIRMED`, con A `available = 7, reserved = 3`
- When llega `C`
- Then O queda `CANCELLED`, se emite `L`, la reserva queda `RELEASED` y A `available = 10, reserved = 0`

### 004-AC-04 · `SR` tardío sobre `CANCELLED` (REQ-27)
- Given O `CANCELLED` (cancelado en `PENDING`)
- When llega `SR`
- Then O sigue `CANCELLED` y se emite `L(COMPENSACION_RESERVA_TARDIA)`
- And tras procesarse ambos `L`, A recupera todo el stock una sola vez

### 004-AC-05 · `L` antes que `R`: tombstone (REQ-12, REQ-27)
- Given no existe la reserva de O
- When inventory consume `L` y después `R`
- Then la reserva queda `RELEASED` (tombstone), `R` responde `StockRechazado(RESERVATION_RELEASED)` y A no cambió nunca

### 004-AC-06 · Timeout con reserva tardía (REQ-27)
- Given O en `PENDING` con `R` demorado
- When vence el timeout (`T`) y después inventory procesa `R` y `L`, y order recibe `SR`
- Then O queda `REJECTED(TIMEOUT)`, la reserva `RELEASED` y A recupera todo el stock

### 004-AC-07 · Timeout concurrente con `SR` (REQ-27, REQ-25)
- Given O en `PENDING` y la reserva `RESERVED`
- When `T` y `SR` se procesan a la vez, repetido 50 veces con pedidos distintos
- Then O termina `CONFIRMED` con la reserva `RESERVED`, o `REJECTED(TIMEOUT)` con la reserva `RELEASED`, nunca una mezcla, y se cumplen C-1 a C-5

### 004-AC-08 · Cancelar `CANCELLED` es idempotente por estado (REQ-09, REQ-21)
- Given O `CANCELLED`
- When se cancela otra vez con otra `Idempotency-Key`
- Then responde `200`, no se emite ningún `L` nuevo y `version` no cambia

### 004-AC-09 · Cancelar `REJECTED` (REQ-21)
- Given O `REJECTED`
- When se cancela
- Then responde `409` con `code = ORDER_NOT_CANCELLABLE` y nada cambia

### 004-AC-10 · Duplicados en cada paso (REQ-14, REQ-26)
- Given cualquiera de los escenarios AC-01 a AC-07
- When cada mensaje (`R`, `L`, `SR`, `SJ`, `StockLiberado`) se entrega 2 o 3 veces con el mismo `idMensaje`
- Then el estado final y el stock son idénticos a los de la entrega única

### 004-AC-11 · `L` duplicado lógico (otro `idMensaje`) (REQ-12, REQ-15)
- Given la reserva de O `RESERVED`
- When llegan dos `L` distintos (cancelación + compensación), también a la vez en 2 instancias de inventory
- Then el stock se devuelve exactamente una vez, la reserva queda `RELEASED` y el segundo `L` responde `StockLiberado(NADA_QUE_LIBERAR)`

### 004-AC-12 · `StockRechazado` imposible sobre `CONFIRMED` (REQ-26)
- Given O `CONFIRMED`
- When llega `SJ`
- Then O sigue `CONFIRMED`, el mensaje va a `inventario.eventos.DLT` como no reintentable y `messages_dlt_total` aumenta

### 004-AC-13 · Propiedad general (REQ-27, REQ-31)
- Given 20 pedidos sobre 3 productos con stock limitado
- When para cada pedido se generan aleatoriamente, con semilla registrada, el orden de `C`, `T`, `R`, `L`, `SR`, `SJ` y los duplicados, respetando el orden por partición (`R` antes que su `L` salvo en AC-05)
- Then al estabilizarse se cumplen C-1 a C-5 para todos los pedidos y productos

## Casos borde

- `C` llega mientras el publisher del outbox aún no envió `R`: `L` queda detrás de `R` en el outbox (mismo agregado, `id` mayor). Inventory recibe `R` y luego `L` (equivale a AC-01).
- Redis no disponible: no se obtiene el lock y la cancelación responde `503` `LOCK_UNAVAILABLE`. Nunca se cancela sin el lock. Los listeners lo tratan como un error reintentable.
- Lease del lock vencido con dos instancias dentro (pausa de GC): la base lo resuelve con `@Version` y una de las transacciones falla y se reintenta. Lo cubre AC-02 ejecutado con el lock deshabilitado.

## Fuera de alcance

- Cancelación parcial (por línea).
- Notificar al cliente el resultado de la cancelación de forma asíncrona.

## Preguntas resueltas

- **P-01** Cancelar un `CONFIRMED` está permitido, coherente con "liberar stock por cancelaciones" (REQ-12). Decidido el 2026-10-09.
- **P-02** `StockRechazado` sobre `CONFIRMED` va a la DLT como no reintentable (RN-09). Decidido el 2026-10-09.
