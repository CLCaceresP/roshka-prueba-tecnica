# 014 · Ajuste de stock con historial de movimientos

Estado: Aprobada
Requisitos: EXT-03, REQ-10, REQ-13, REQ-24, REQ-31
Decisiones: [ADR 0022](../../docs/adr/0022-ajuste-de-stock-con-put-absoluto.md)

## Contexto

Un producto se crea con un stock inicial y no hay forma de reponerlo ni de retirar unidades. Esta spec agrega `PUT /products/{productId}/stock`, que fija cuántas unidades hay disponibles, y un historial de los cambios. No hay estado habilitado/deshabilitado: un producto agotado se reconoce por `available = 0`.

Se usa `PUT` porque el cuerpo describe el estado final del recurso (`available = N`), así que repetir la misma solicitud deja el mismo resultado. Un delta («sumar 10») no es un `PUT`.

## Reglas de negocio

- **RN-01** `PUT /products/{productId}/stock` con `{"available": n, "reason": "texto opcional"}` deja `available = n`. `reserved` no cambia y `total = n + reserved`, así que `available + reserved = total` se mantiene (invariantes 1 y 2). Responde `200` con `productId`, `total`, `available`, `reserved` y `movement` (el movimiento registrado, o `null` si no hubo cambio).
- **RN-02** `available` va de 0 a 10.000.000. Las unidades reservadas no se tocan: bajar `available` a 0 no cancela reservas.
- **RN-03** Si `n + reserved` supera 10.000.000, responde `409 STOCK_LIMIT_EXCEEDED` y no cambia nada.
- **RN-04** Si `n` es igual al `available` actual, responde `200` sin cambios y sin movimiento (`movement = null`).
- **RN-05** Cuando hay cambio, se registra un movimiento: `ADD` si subió, `REMOVE` si bajó, con `quantity` igual a la diferencia, `totalAfter` y `availableAfter`. El `UPDATE` y el `INSERT` ocurren en la misma transacción.
- **RN-06** La fila del producto se bloquea mientras se lee el valor anterior y se escribe el nuevo, así que la diferencia del movimiento es exacta aunque haya reservas concurrentes. El valor final siempre es el pedido: un `PUT` pisa lo que otra operación haya dejado en `available`.
- **RN-07** `reason` es opcional, de hasta 200 caracteres, sin caracteres de control; se normaliza como el nombre del producto.
- **RN-08** Exige `Idempotency-Key` (invariante 12): misma key y mismo cuerpo → la misma respuesta y un solo movimiento; misma key y cuerpo distinto → `422`; key en proceso → `409` con `Retry-After`.
- **RN-09** `GET /products/{productId}/stock/movements` lista el historial paginado (como la [spec 012](../012-listado-de-productos/spec.md)), del más reciente al más antiguo con desempate por `movementId`. Cada movimiento trae `movementId`, `type`, `quantity`, `reason`, `createdAt`, `totalAfter` y `availableAfter`.
- **RN-10** Un producto desconocido responde `404 PRODUCT_NOT_FOUND`. Un `available` fuera de rango, un `reason` inválido o una propiedad desconocida responden `400 VALIDATION_FAILED`.
- **RN-11** La creación de un producto con stock inicial no genera movimiento.
- **RN-12** Los logs cuentan el cambio: `Stock del producto <id> fijado en 15 disponibles (antes 5): se agregaron 10 unidades`.

## Criterios de aceptación

### 014-AC-01 · Subir el stock (EXT-03)
- Given un producto con 5 unidades disponibles
- When llega `PUT /products/{id}/stock` con `available = 15` y una `Idempotency-Key`
- Then responde `200` con `total = 15`, `available = 15`, `reserved = 0`
- And existe un movimiento `ADD` de 10 con `totalAfter = 15`

### 014-AC-02 · Bajar el stock sin tocar lo reservado (EXT-03, REQ-10)
- Given un producto con `available = 8` y `reserved = 2`
- When llega un `PUT` con `available = 5`
- Then queda con `total = 7`, `available = 5`, `reserved = 2`, y un movimiento `REMOVE` de 3

### 014-AC-03 · Se puede dejar el stock en cero (EXT-03, REQ-13)
- Given un producto con `available = 3` y `reserved = 5`
- When llega un `PUT` con `available = 0`
- Then queda con `total = 5`, `available = 0`, `reserved = 5` y las reservas siguen vigentes

### 014-AC-04 · El total tiene un tope (EXT-03)
- Given un producto con `reserved = 5`
- When llega un `PUT` con `available = 10.000.000`
- Then responde `409 STOCK_LIMIT_EXCEEDED` y no cambia nada

### 014-AC-05 · Validaciones (REQ-19)
- Given un producto desconocido, `available` negativo o mayor que 10.000.000, un `reason` de más de 200 caracteres, sin `available` o una propiedad desconocida
- When llega el `PUT`
- Then responde `404 PRODUCT_NOT_FOUND` o `400 VALIDATION_FAILED` según corresponda, y no se registra ningún movimiento

### 014-AC-06 · Sin cambio no hay movimiento (EXT-03)
- Given un producto con `available = 5`
- When llega un `PUT` con `available = 5`
- Then responde `200` con `movement = null` y el historial no cambia

### 014-AC-07 · Idempotencia (REQ-24)
- Given un `PUT` ya procesado
- When se repite con la misma `Idempotency-Key` y el mismo cuerpo
- Then reproduce la respuesta original (`Idempotent-Replayed: true`) y hay un solo movimiento
- And con la misma key y otro cuerpo responde `422 IDEMPOTENCY_KEY_REUSED`
- And sin key responde `400 IDEMPOTENCY_KEY_MISSING`

### 014-AC-08 · Concurrencia (REQ-31)
- Given un producto
- When 50 `PUT` con valores distintos llegan al mismo tiempo
- Then el producto queda con el valor de uno de ellos, `available + reserved = total`, y cada movimiento registrado tiene `quantity` igual a la diferencia que vio al escribir (la suma de los deltas explica el valor final)
- And con la misma key repetida 20 veces simultáneas hay un solo movimiento

### 014-AC-09 · Los ajustes conviven con las reservas (REQ-13, REQ-31)
- Given reservas, liberaciones y `PUT` concurrentes sobre el mismo producto
- Then al final `available >= 0`, `reserved >= 0` y `available + reserved = total`

### 014-AC-10 · Historial paginado (EXT-03)
- Given varios cambios sobre un producto
- When llega `GET /products/{productId}/stock/movements?page=0&size=2`
- Then devuelve la página con los movimientos más recientes primero, con `totalElements` y `totalPages`

### 014-AC-11 · Los logs cuentan el cambio (BON-03)
- Given un `PUT`
- Then el log dice el valor anterior, el nuevo, cuántas unidades se agregaron o retiraron y los pasos con sus tiempos

## Casos borde
- Fijar `available` en 0 deja el producto existente y repuesto-able; las reservas vivas siguen su curso.
- Un `PUT` que llega entre una reserva y su liberación pisa el `available`: las unidades liberadas después se suman al nuevo valor.
- Un `409` por tope no se guarda como respuesta idempotente (ADR 0011): reintentar con la misma key reevalúa.

## Fuera de alcance
- Sumar o restar un delta (no es un `PUT`) y ajustar varios productos a la vez.
- Estado habilitado/deshabilitado del producto.

## Preguntas abiertas
Ninguna.
