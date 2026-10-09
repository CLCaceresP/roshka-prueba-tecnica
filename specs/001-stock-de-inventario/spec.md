# 001 · Stock de inventario

Estado: Aprobada
Requisitos: REQ-10, REQ-11, REQ-12, REQ-13, REQ-15, REQ-16, REQ-22, REQ-23

## Contexto

Inventory Service es dueño del stock. Esta spec define el modelo de stock, la carga de productos, la consulta y las operaciones de reserva y liberación **a nivel de dominio y persistencia**. Cómo llegan esas operaciones por mensajería está en la [003](../003-saga-de-reserva/spec.md). El orden de llegada y los duplicados, en la [004](../004-cancelacion-contra-reserva/spec.md). La idempotencia HTTP de `POST /products`, en la [005](../005-idempotencia-http/spec.md).

## Modelo

- **Producto:** `productId` (UUID generado por el servidor), `name`, `total`, `available`, `reserved`.
- **Reserva:** una por pedido (`orderId`), con estado `RESERVED`, `RELEASED` o `REJECTED`, sus líneas (`productId`, `quantity`) y, si fue rechazada, un motivo.

## Reglas de negocio

- **RN-01** `available >= 0` y `reserved >= 0` siempre (invariante 1).
- **RN-02** `available + reserved = total` siempre (invariante 2). `total` solo se fija al crear el producto.
- **RN-03** Una reserva es **todo o nada**: si alguna línea no se puede satisfacer, no se modifica el stock de ninguna (invariante 3).
- **RN-04** La reserva descuenta con un `UPDATE` condicional (`WHERE available >= :qty`). La corrección no depende de locks ni de lecturas previas.
- **RN-05** En una reserva con varias líneas, los productos se actualizan en **orden ascendente de `productId`**, para que dos reservas concurrentes no se bloqueen mutuamente (deadlock).
- **RN-06** Liberar una reserva `RESERVED` devuelve a `available` exactamente lo que se reservó en cada línea, y la reserva pasa a `RELEASED`. Una reserva libera stock como máximo una vez (invariante 5).
- **RN-07** Una reserva sobre un producto inexistente se rechaza completa con el motivo `UNKNOWN_PRODUCT`.
- **RN-08** Una reserva sin stock suficiente se rechaza completa con el motivo `INSUFFICIENT_STOCK` e indica los `productId` que no alcanzaron.
- **RN-09** `POST /products` crea un producto con `initialQuantity >= 0`: `total = available = initialQuantity` y `reserved = 0`.
- **RN-10** Los datos de inventario viven solo en `inventory_db`, accedida con un usuario sin permisos sobre `orders_db` (REQ-16).

## API

### `POST /products`

- Header obligatorio `Idempotency-Key` (ver 005).
- Body: `{ "name": string (1..120), "initialQuantity": int (0..1_000_000) }`
- `201 Created`, `Location: /products/{productId}`, body `{ productId, name, total, available, reserved }`
- `400` `VALIDATION_FAILED` si el body es inválido.

### `GET /products/{productId}/stock`

- `200` `{ productId, total, available, reserved }`
- `404` `PRODUCT_NOT_FOUND`
- `400` `VALIDATION_FAILED` si `productId` no es un UUID.

Todos los errores van como `ProblemDetail` (RFC 9457) con la propiedad `code`.

## Criterios de aceptación

### 001-AC-01 · Crear producto con stock inicial (REQ-10, REQ-22)
- Given no existe el producto
- When `POST /products` con `{ "name": "Teclado", "initialQuantity": 10 }`
- Then responde `201` con `Location` y `total = 10, available = 10, reserved = 0`

### 001-AC-02 · Consultar stock (REQ-23)
- Given un producto con `total = 10`, del que se reservaron 3
- When `GET /products/{id}/stock`
- Then responde `200` con `available = 7, reserved = 3, total = 10`

### 001-AC-03 · Producto inexistente (REQ-23)
- When `GET /products/{uuid-inexistente}/stock`
- Then responde `404` con `code = PRODUCT_NOT_FOUND`

### 001-AC-04 · Reserva exitosa de varias líneas (REQ-11)
- Given A con `available = 5` y B con `available = 2`
- When se reserva el pedido O con A×3 y B×2
- Then A queda `available = 2, reserved = 3`, B queda `available = 0, reserved = 2`, y la reserva de O queda `RESERVED`

### 001-AC-05 · Reserva todo o nada (REQ-11, REQ-13)
- Given A con `available = 5` y B con `available = 1`
- When se reserva el pedido O con A×3 y B×2
- Then la reserva queda `REJECTED` con `INSUFFICIENT_STOCK` y `[B]`, y A y B no cambian

### 001-AC-06 · Producto desconocido rechaza la reserva completa (REQ-11)
- Given A existe y X no existe
- When se reserva el pedido O con A×1 y X×1
- Then la reserva queda `REJECTED` con `UNKNOWN_PRODUCT` y A no cambia

### 001-AC-07 · Nunca negativo, garantizado por la base (REQ-13)
- Given A con `available = 1`
- When se ejecuta directamente en SQL un `UPDATE` que dejaría `available = -1`
- Then la base lo rechaza por el `CHECK`

### 001-AC-08 · Liberar devuelve exactamente lo reservado (REQ-12)
- Given la reserva de O en `RESERVED` con A×3 y B×2
- When se libera la reserva de O
- Then A y B recuperan 3 y 2 unidades en `available`, `reserved` baja en lo mismo, y la reserva queda `RELEASED`

### 001-AC-09 · Reservas concurrentes no sobrevenden (REQ-13, REQ-31)
- Given A con `available = 10`
- When 100 pedidos distintos reservan A×1 a la vez
- Then exactamente 10 reservas quedan `RESERVED` y 90 `REJECTED`, y A queda `available = 0, reserved = 10`

### 001-AC-10 · Sin deadlocks con líneas cruzadas (REQ-11, REQ-31)
- Given A y B con stock suficiente
- When 50 pedidos reservan [A, B] y 50 pedidos reservan [B, A] a la vez
- Then todas las reservas terminan, ninguna falla por deadlock y `available + reserved = total` en A y B

### 001-AC-11 · Validación de entrada (REQ-22)
- When `POST /products` con `initialQuantity = -1`, o `name` vacío o de más de 120 caracteres
- Then responde `400` con `code = VALIDATION_FAILED` y no se crea nada

## Casos borde

- `initialQuantity = 0`: es válido; cualquier reserva sobre ese producto se rechaza.
- Una reserva con el mismo `productId` en dos líneas no puede llegar: Order lo rechaza (002, RN-04). Si llegara, el contrato se valida y el mensaje va a la DLT (003).
- Cantidades muy grandes: `quantity` y `initialQuantity` están acotadas, por lo que no hay desbordamiento de `int`.

## Fuera de alcance

- Reponer stock de un producto existente, modificarlo o borrarlo.
- Transformar una reserva en venta o envío. `CONFIRMED` mantiene el stock en `reserved`.
- Búsqueda y listado de productos.

## Preguntas abiertas

- Ninguna. La semántica de `POST /products` (crear con stock inicial) ya está decidida.
