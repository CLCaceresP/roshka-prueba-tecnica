# 002 · Crear y consultar pedidos

Estado: Aprobada
Requisitos: REQ-03, REQ-04, REQ-06, REQ-08, REQ-09, REQ-16, REQ-19, REQ-20

## Contexto

Order Service es dueño del agregado `Pedido`. Esta spec define la creación y la consulta de pedidos, y la máquina de estados del agregado. El flujo de respuestas de inventory está en la [003](../003-saga-de-reserva/spec.md). La cancelación y la carrera, en la [004](../004-cancelacion-contra-reserva/spec.md). La idempotencia HTTP, en la [005](../005-idempotencia-http/spec.md).

## Modelo

- **Order:** `orderId` (UUID generado por el servidor), `status`, `lines` (`productId`, `quantity`), `rejectionReason` (si `REJECTED`), `createdAt`, `updatedAt` y `version`.
- **Máquina de estados** (patrón State en el dominio, invariante 6):

```
PENDING ──StockReservado──► CONFIRMED ──cancel──► CANCELLED
   │ ──StockRechazado / timeout──► REJECTED
   └──cancel──────────────────────► CANCELLED
```

`REJECTED` y `CANCELLED` son terminales. Los eventos tardíos sobre estados terminales no cambian el estado (tabla completa en la 004).

## Reglas de negocio

- **RN-01** Un pedido nace en `PENDING`.
- **RN-02** Crear un pedido escribe el `Pedido` y el comando `ReservarStock` en el outbox **en la misma transacción** (invariante 7). Kafka nunca se llama desde el caso de uso.
- **RN-03** Un pedido tiene entre 1 y 50 líneas, con `quantity` entre 1 y 1000.
- **RN-04** Un pedido no puede repetir el mismo `productId` en dos líneas: `400` `DUPLICATE_PRODUCT_LINE`.
- **RN-05** Order Service no consulta a inventory al crear (no hay llamadas síncronas). La existencia del producto y el stock los resuelve inventory de forma asíncrona.
- **RN-06** Una transición no permitida por la máquina de estados es un error de dominio (`TransicionPedidoInvalida`). En HTTP se traduce a `409`.
- **RN-07** Los datos viven solo en `orders_db` (REQ-16).

## API

### `POST /orders`

- Header obligatorio `Idempotency-Key` (005).
- Body: `{ "lines": [ { "productId": uuid, "quantity": int } ] }`
- `202 Accepted`, `Location: /orders/{orderId}`, body `{ orderId, status: "PENDING", lines, createdAt }`
- `400` `VALIDATION_FAILED` o `DUPLICATE_PRODUCT_LINE`

### `GET /orders/{orderId}`

- `200` `{ orderId, status, rejectionReason?, lines, createdAt, updatedAt }`
- `404` `ORDER_NOT_FOUND`

## Criterios de aceptación

### 002-AC-01 · Crear pedido (REQ-03, REQ-19)
- Given un body válido y una `Idempotency-Key` nueva
- When `POST /orders`
- Then responde `202`, `Location: /orders/{id}` y `status = PENDING`

### 002-AC-02 · El comando sale por el outbox en la misma transacción (REQ-06)
- Given un pedido válido
- When se crea el pedido
- Then en la misma transacción existe una fila del outbox `ReservarStock` con `idAgregado = idPedido`, las líneas del pedido, key = `idPedido` y topic `inventario.comandos`

### 002-AC-03 · Rollback sin mensaje (REQ-06)
- Given un fallo forzado al persistir el pedido
- When se intenta crear
- Then no existe ni el pedido ni la fila del outbox

### 002-AC-04 · Consultar estado (REQ-04, REQ-20)
- Given un pedido existente
- When `GET /orders/{id}`
- Then responde `200` con su estado actual

### 002-AC-05 · Pedido inexistente (REQ-20)
- When `GET /orders/{uuid-inexistente}`
- Then responde `404` con `code = ORDER_NOT_FOUND`

### 002-AC-06 · Validaciones (REQ-19)
- When `POST /orders` sin líneas, con `quantity = 0`, con más de 50 líneas o con un `productId` repetido
- Then responde `400` con el `code` correspondiente y no se crea nada ni se escribe en el outbox

### 002-AC-07 · Máquina de estados (REQ-08)
- Given un `Pedido` en cada estado
- When se aplica cada transición posible
- Then solo las transiciones del diagrama cambian el estado; las demás lanzan `TransicionPedidoInvalida` o son no-ops definidos en la 004

## Casos borde

- Un pedido con un producto inexistente se acepta (`202`) y termina `REJECTED` con `UNKNOWN_PRODUCT` (003).
- El cliente consulta un pedido inmediatamente después de crearlo: ve `PENDING`. La consistencia es eventual.

## Fuera de alcance

- Listar pedidos, clientes, precios, pagos y envío.
- Modificar las líneas de un pedido existente.

## Preguntas abiertas

- Ninguna.
