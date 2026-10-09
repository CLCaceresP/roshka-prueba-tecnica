# 0005 · Bloqueo distribuido con Redisson como defensa en profundidad

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-02, REQ-09, REQ-13, REQ-15, REQ-24, REQ-25, REQ-27

## Contexto y problema

El enunciado exige bloqueo distribuido (REQ-25). Hay dos puntos donde varias instancias compiten por el mismo recurso:

1. **La misma `Idempotency-Key` llega a la vez a dos instancias** (reintento del cliente o del gateway). Sin coordinación, ambas podrían ejecutar el caso de uso.
2. **El mismo pedido recibe a la vez una cancelación HTTP y una respuesta de la saga** (REQ-27).

Un lock distribuido sobre Redis **no es un mecanismo de corrección**. Si el lease vence durante una pausa de GC o una partición de red, dos procesos creen tener el lock, y Redis con un solo nodo no ofrece fencing tokens. Si la ausencia de sobreventa dependiera del lock, el sistema sería incorrecto en esos casos.

## Factores de decisión

- La corrección no puede depender de un lock que puede fallar.
- Los locks tienen que reducir trabajo duplicado y conflictos, no ser la única barrera.
- Nunca bloquear indefinidamente.

## Opciones consideradas

1. **Sin lock distribuido; solo la base de datos** (PK, `UPDATE` condicional, `@Version`). Es correcto, pero no cumple REQ-25 y genera más conflictos y reintentos bajo contención.
2. **Redisson (Redis)** como primera barrera, con la base como garantía final.
3. **Advisory locks de Postgres.** Funcionan, pero cada servicio solo puede coordinar dentro de su propia base y atan el lock a una conexión (y a una transacción) abierta.
4. **Redlock multi-nodo.** Más robusto ante la caída de un nodo de Redis, pero sigue sin fencing y es desproporcionado para la prueba.

## Decisión

**Opción 2, explícitamente como defensa en profundidad.**

| Lock | Dónde | Protege | Si no se obtiene |
|---|---|---|---|
| `idem:{service}:{key}` | Alrededor del procesamiento de una `Idempotency-Key` | Ejecuciones concurrentes del mismo request entre instancias | `409 Conflict` + `Retry-After` |
| `order:{orderId}` | Cancelación y manejo de respuestas de la saga | Interleaving cancelación/respuesta | HTTP: `409` + `Retry-After`; mensaje: excepción reintentable (backoff bloqueante, [0006](0006-reintentos-bloqueantes-y-dlt.md)) |

- Todo lock usa `tryLock(waitTime, leaseTime)` con valores en `application.yml` (`bloqueo.espera-bloqueo`, `locking.lease-time`). Nunca `lock()` sin límite. Se libera en `finally` solo si el hilo actual lo tiene.
- El lock se toma **fuera** de la transacción de base y la envuelve, para que otra instancia no lea un estado aún no commiteado.
- **La garantía final es la base de datos:**
  - Sobreventa: `UPDATE products SET available = available - :qty, reserved = reserved + :qty WHERE id = :id AND available >= :qty` y `CHECK (available >= 0)` (invariante 1).
  - Idempotencia HTTP: PK sobre `idempotency_keys.key`.
  - Mensajes duplicados: PK sobre `processed_messages.message_id`.
  - Concurrencia sobre un pedido: `@Version` en `Pedido`, y además fuerza de versión al escribir en el outbox ([0003](0003-outbox-con-polling-y-shedlock.md)).
- El tiempo de espera del lock se mide con `distributed_lock_wait_seconds`.

## Consecuencias

- Positivas: cumple REQ-25 y reduce los conflictos optimistas bajo contención. Si Redis falla o el lease vence, el sistema sigue siendo correcto; solo baja la eficiencia.
- Negativas: Redis entra en el camino crítico de escritura. Si Redis no está disponible, los endpoints de escritura responden `503` en vez de degradar a solo-DB. Es una decisión explícita: preferir fallar visible a operar sin la barrera pedida por el enunciado.
- Riesgos: un lease demasiado corto produce ejecuciones solapadas. La base lo absorbe y la métrica de conflictos lo hace visible.

## Nota posterior (revisión final)

Con Redis caído por más tiempo que el backoff de los consumidores (`mensajeria.reintentos.tiempo-maximo-transcurrido`), las respuestas de inventory terminan en la DLT. El timeout de saga cierra esos pedidos como `REJECTED(TIMEOUT)` y libera su stock, así que el estado final es consistente (spec 004, C-1 a C-4), pero se pierden confirmaciones. Queda como limitación conocida en el README.

## Cómo se verifica

- Test de concurrencia 2 (50 requests con la misma key): un solo pedido creado.
- Test con el lock deshabilitado (puerto con implementación nula en el test): las garantías de stock y de pedido único se mantienen solo con la base. Demuestra que el lock no es la barrera de corrección.
- Test: no obtener el lock en HTTP → `409` con `Retry-After`.

## Referencias

- Martin Kleppmann, "How to do distributed locking" (2016).
- Redisson `RLock#tryLock(long, long, TimeUnit)`.
