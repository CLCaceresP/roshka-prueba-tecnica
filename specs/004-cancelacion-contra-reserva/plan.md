# 004 · Plan técnico

## Dónde vive cada decisión

| Decisión | Servicio | Capa | Elemento |
|---|---|---|---|
| Transición de estado de `Pedido` | ms-pedidos | `dominio` | `Pedido` + estados (patrón State): `cancel()`, `alStockReservado()`, `alStockRechazado()`, `alTimeout()` devuelven el resultado de la transición (nuevo estado + eventos de dominio) |
| Compensación ante un evento tardío | ms-pedidos | `aplicacion/saga` | `ReservationSagaOrchestrator` traduce los eventos de dominio en comandos (`LiberarStock`) |
| Cancelación HTTP | ms-pedidos | `aplicacion` | `CancelarPedido` (lock `order:{id}` → transacción → outbox) |
| Tabla reserva × comando | ms-inventario | `dominio` | `Reserva` con `onReserve(...)` y `onRelease()` que devuelven el resultado y la respuesta a emitir |
| Serialización por pedido | ms-inventario | `adaptadores.salida.persistence` | Insert con PK / `SELECT ... FOR UPDATE` sobre `reservations` |

Las dos tablas de la spec se implementan como **tests parametrizados del dominio** (una fila = un caso) antes que cualquier integración.

## Lock y transacción

```
tryLock(order:{id}, wait, lease)            // fuera de la transacción
  └─ @Transactional
       ├─ cargar Order (con version)
       ├─ transición en el dominio
       ├─ outbox.append(...) + OPTIMISTIC_FORCE_INCREMENT
       └─ commit                            // conflicto de versión → excepción
unlock (finally, solo si lo tiene el hilo)
```

- HTTP: no obtener el lock o tener un conflicto de versión → `409 CONCURRENT_MODIFICATION` + `Retry-After`.
- Listener: no obtener el lock o tener un conflicto de versión → excepción reintentable, con backoff bloqueante.

## Cómo se fuerzan los órdenes de llegada en los tests

- **Nivel dominio (unit):** las tablas completas, sin infraestructura.
- **Nivel servicio (IT, un servicio):** los mensajes se inyectan directamente en el topic con un `KafkaTemplate` de test. Así se controlan el orden, los duplicados y los `idMensaje`. Se usa Awaitility sobre el estado en la base.
- **Concurrencia (AC-02, 07, 11):** `ExecutorService` con N hilos que esperan en un `CountDownLatch` y llaman en paralelo a los puertos `in` (HTTP cancel y handler de evento). Dos contextos de aplicación o dos consumidores en el mismo group simulan las dos instancias.
- **Lock deshabilitado:** se repiten AC-02 y AC-11 con un adaptador de lock que siempre concede, para demostrar que la base sola mantiene la corrección ([ADR 0005](../../docs/adr/0005-bloqueo-distribuido-como-defensa-en-profundidad.md)).
- **Propiedad (AC-13):** generador de secuencias con semilla fija (registrada en el nombre del caso); las aserciones C-1 a C-5 se implementan una sola vez como `ConsistencyAssertions`.

## Tests por criterio

| Criterio | Nivel |
|---|---|
| Tablas Order e Inventory | unit parametrizado |
| AC-01, 03, 04, 05, 06, 08, 09, 12 | it |
| AC-02, 07, 11 | concurrency |
| AC-10 | it (las mismas secuencias, con entregas duplicadas) |
| AC-13 | concurrency (propiedad con semilla) |

## ADRs relacionados

[0003](../../docs/adr/0003-outbox-con-polling-y-shedlock.md), [0004](../../docs/adr/0004-saga-orquestada-dentro-de-ms-pedidos.md), [0005](../../docs/adr/0005-bloqueo-distribuido-como-defensa-en-profundidad.md), [0006](../../docs/adr/0006-reintentos-bloqueantes-y-dlt.md). Por escribir: tombstone de reservas (Fase 2) y máquina de estados (Fase 3).
