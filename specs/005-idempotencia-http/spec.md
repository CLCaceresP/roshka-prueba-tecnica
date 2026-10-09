# 005 · Idempotencia HTTP

Estado: Aprobada
Requisitos: REQ-09, REQ-15, REQ-24, REQ-25, REQ-31

## Contexto

Los clientes reintentan ante timeouts y errores de red. Un `POST /orders` reintentado no puede crear dos pedidos, y un `POST /products` reintentado no puede crear dos productos (REQ-24). Además, con varias instancias, dos copias del mismo request pueden llegar a la vez a instancias distintas (REQ-02), lo que exige bloqueo distribuido (REQ-25).

Endpoints alcanzados: `POST /orders`, `DELETE /orders/{orderId}`, `POST /products` y `PUT /products/{productId}/stock`.

## Reglas de negocio

- **RN-01** Todo endpoint de escritura exige el header `Idempotency-Key`, de 1 a 100 caracteres `[A-Za-z0-9_.:-]`. Se recomienda un UUID. Si falta → `400` `IDEMPOTENCY_KEY_MISSING`. Si no cumple el formato → `400` `IDEMPOTENCY_KEY_INVALID`. El gateway hace el mismo chequeo de presencia como rechazo temprano, pero el servicio es la fuente de verdad ([ADR 0007](../../docs/adr/0007-gateway-delgado.md)).
- **RN-02** `request_hash` = SHA-256 de `método + ruta + body JSON canónico` (claves ordenadas, sin espacios). Una misma key usada en otra ruta cuenta como otro request.
- **RN-03** Misma key + mismo hash, con la key ya completada → se devuelve **la respuesta almacenada**: mismo status, mismo body y headers relevantes (`Location`), más `Idempotent-Replayed: true`. No se ejecuta nada. Es la respuesta original, no el estado actual del recurso; para el estado actual está el `GET`.
- **RN-04** Misma key + otro hash → `422` `IDEMPOTENCY_KEY_REUSED`, sin efectos.
- **RN-05** Misma key mientras otro request con esa key está en proceso → `409` `IDEMPOTENCY_KEY_IN_PROGRESS` con `Retry-After: <idempotencia.reintentar-despues>`.
- **RN-06** Solo se almacenan las respuestas **2xx**. Una respuesta de error implica que la transacción hizo rollback y no hubo efectos, así que el cliente puede corregir y reintentar con la misma key.
- **RN-07** La fila de la key se escribe **en la misma transacción** que el efecto de negocio y su outbox. No puede existir un efecto sin su key ni una key sin su efecto.
- **RN-08** Las keys vencen a los `idempotencia.ttl` (24 h por defecto). Una key vencida se trata como nueva. Un job con ShedLock purga las vencidas.
- **RN-09** **Bloqueo distribuido.** El procesamiento de una key ocurre bajo el lock `idem:{service}:{key}` (`tryLock` con espera y lease configurables). Si no se obtiene, aplica RN-05. Es defensa en profundidad: si el lock falla, la PK de `idempotency_keys` serializa en la base ([ADR 0005](../../docs/adr/0005-bloqueo-distribuido-como-defensa-en-profundidad.md)).

## Criterios de aceptación

### 005-AC-01 · Falta la key (REQ-24)
- When se llama a cualquiera de los tres endpoints de escritura sin `Idempotency-Key`
- Then responde `400` con `code = IDEMPOTENCY_KEY_MISSING` y no hay efectos

### 005-AC-02 · Replay con el mismo body (REQ-09, REQ-24)
- Given un `POST /orders` completado con la key K
- When se repite con K y el mismo body
- Then responde `202` con el mismo `orderId`, body y `Location`, más `Idempotent-Replayed: true`
- And existe un solo pedido y una sola fila de outbox

### 005-AC-03 · Key reutilizada con otro body (REQ-24)
- Given un request completado con K
- When llega otro request con K y un body distinto
- Then responde `422` con `code = IDEMPOTENCY_KEY_REUSED` y no hay efectos

### 005-AC-04 · Key reutilizada en otra ruta (REQ-24)
- Given `POST /orders` completado con K
- When llega `POST /products` (o un cancel) con K
- Then responde `422` en ms-pedidos. En ms-inventario, que tiene su propia tabla, la key es nueva: el alcance es por servicio.

### 005-AC-05 · Key en proceso (REQ-24, REQ-25)
- Given el lock `idem:order:K` está tomado por otro request
- When llega un request con K
- Then responde `409` con `code = IDEMPOTENCY_KEY_IN_PROGRESS` y `Retry-After`

### 005-AC-06 · 50 requests concurrentes con la misma key (REQ-24, REQ-25, REQ-31)
- Given 50 hilos que esperan en un `CountDownLatch`, repartidos entre 2 instancias
- When todos envían `POST /orders` con K y el mismo body
- Then existe exactamente un pedido y una fila de outbox
- And todas las respuestas son `202` idénticas (mismo `orderId`) o `409 IDEMPOTENCY_KEY_IN_PROGRESS`
- And tras reintentar los `409`, las 50 respuestas finales son `202` idénticas

### 005-AC-07 · La base sostiene la garantía sin lock (REQ-24)
- Given el adaptador de lock concede siempre (lock deshabilitado)
- When se repite AC-06
- Then sigue existiendo un solo pedido, y las respuestas son `202` idénticas porque los concurrentes esperan a la PK y luego hacen replay

### 005-AC-08 · Los errores no se almacenan (REQ-24)
- Given `POST /orders` con K y un body inválido → `400`
- When se repite con K y un body válido
- Then responde `202` y crea el pedido

### 005-AC-09 · Vencimiento (REQ-24)
- Given una key completada cuyo `expires_at` ya pasó
- When llega un request con esa key
- Then se procesa como nuevo

### 005-AC-10 · Idempotencia de `POST /products` (REQ-15, REQ-22, REQ-24)
- Given `POST /products` completado con K
- When se repite con K y el mismo body
- Then responde el mismo `201` con el mismo `productId`, y existe un solo producto

### 005-AC-11 · Idempotencia del cancel (REQ-09, REQ-21, REQ-24)
- Given un cancel completado con K
- When se repite con K
- Then responde el `200` almacenado y no se emite otro `LiberarStock`

## Casos borde

- Un body con las mismas claves en otro orden o con otros espacios produce el mismo hash (JSON canónico).
- Redis no disponible: `503` `LOCK_UNAVAILABLE`. No se procesa sin el lock (ver 004).
- El cliente envía la misma key a order y a inventory: cada servicio tiene su propia tabla y las trata como independientes.

## Fuera de alcance

- Idempotencia de los `GET`: ya son seguros por definición.
- Compartir keys entre servicios.

## Preguntas resueltas

- **P-01 · Columna `status`.** Decidido el 2026-10-09: se elimina `status` y se agrega `response_headers`. El prompt define `idempotency_keys(key, request_hash, status, response_code, response_body, created_at, expires_at)`. Con RN-07 (key y efecto en una sola transacción), una fila en proceso nunca es visible para otra transacción: solo existen filas completadas. Así `status` siempre valdría lo mismo, y "en proceso" se detecta por el lock (RN-05) o, si el lock falla, por la espera sobre la PK.
  - **Recomendado:** eliminar `status` y agregar `response_headers` (para `Location`).
  - La alternativa es el diseño en dos fases (insertar `IN_PROGRESS`, commit, ejecutar y actualizar a `COMPLETED`). Hace visible el estado "en proceso", pero deja keys colgadas si el proceso cae entre fases y obliga a gestionar su expiración. Es más complejo y más débil.
