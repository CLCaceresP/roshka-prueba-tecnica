# 003 · Plan técnico

## Componentes

| Servicio | Capa | Elementos |
|---|---|---|
| ambos | `adaptadores.salida.messaging` | `OutboxPublisher` (`@Scheduled` + `@SchedulerLock`), `KafkaTemplate` configurado con `enable.idempotence=true` y `acks=all` |
| ambos | `adaptadores.entrada.messaging` | Listeners con ack manual que deserializan el envelope y delegan en un puerto `in` |
| ambos | `configuracion` | `DefaultErrorHandler` + `ExponentialBackOff` + `DeadLetterPublishingRecoverer`, `ErrorClassification` (Strategy) |
| ms-inventario | `aplicacion` | `ManejarReservarStock`, `ManejarLiberarStock` |
| ms-pedidos | `aplicacion/saga` | `ReservationSagaOrchestrator`: `alStockReservado`, `alStockRechazado`, `alStockLiberado`, `alTimeout`. Decide el comando de compensación. |
| ms-pedidos | `adaptadores.entrada.scheduling` | `TimeoutSagaJob` (ShedLock) |

## Consumidor idempotente

En una transacción:

1. `insert into processed_messages(message_id, processed_at) values (?, now()) on conflict do nothing`.
2. Si afectó 0 filas es un duplicado: se incrementa la métrica y se retorna sin efecto. Se usa `ON CONFLICT` y no se captura la excepción de PK, porque en Postgres una excepción aborta la transacción entera.
3. Efecto de negocio según las tablas de la 004.
4. Escritura de la respuesta en el outbox con incremento forzado de la versión del agregado ([ADR 0003](../../docs/adr/0003-outbox-con-polling-y-shedlock.md)).
5. Commit, y luego `Acknowledgment.acknowledge()`.

Si dos instancias procesan el mismo `idMensaje` a la vez, la segunda se bloquea en el insert hasta que la primera commitea y después obtiene 0 filas.

## Migraciones adicionales

- `ms-inventario` `V2__messaging.sql`: `processed_messages`, `outbox` (misma forma que en `ms-pedidos`) y `shedlock`.
- `ms-pedidos` `V2__messaging.sql`: `processed_messages` y `shedlock`. El `outbox` ya está en V1.

## Configuración (`application.yml`)

```yaml
outbox.publicador:
  intervalo-sondeo: 200ms
  tamanio-lote: 100
  bloqueo-maximo: 30s
saga.timeout:
  umbral: 30s
  intervalo-verificacion: 5s
  tamanio-lote: 100
mensajeria.reintentos:
  intervalo-inicial: 200ms
  multiplicador: 2.0
  intervalo-maximo: 5s
  tiempo-maximo-transcurrido: 30s
```

Los valores definitivos se fijan en la implementación con justificación. `tiempo-maximo-transcurrido` debe quedar por debajo de `max.poll.interval.ms`.

## Tests

| Criterio | Nivel |
|---|---|
| AC-04, 05, 06 | Unit de los handlers con fakes + IT con Testcontainers Kafka |
| AC-01, 02, 03 | IT de punta a punta con dos servicios en el mismo test (dos contextos Spring) o con un mensaje simulado del otro lado; se decide en la Fase 3 |
| AC-07 | IT: consumidor de prueba que verifica key y topic |
| AC-08 | IT con un fallo inyectado en un puerto `out` de prueba |
| AC-09 | IT con dos `OutboxPublisher` contra la misma base |
| AC-10 | Unit (`Clock` fijo) + IT |
| AC-11, 12 | IT del error handler |
| AC-13 | `*ConcurrencyIT` (escenario 5) |

## ADRs relacionados

[0002](../../docs/adr/0002-kafka-como-sistema-de-mensajeria.md), [0003](../../docs/adr/0003-outbox-con-polling-y-shedlock.md), [0004](../../docs/adr/0004-saga-orquestada-dentro-de-ms-pedidos.md), [0006](../../docs/adr/0006-reintentos-bloqueantes-y-dlt.md). Por escribir: consumidor idempotente (Fase 2) y timeout de saga (Fase 3).
