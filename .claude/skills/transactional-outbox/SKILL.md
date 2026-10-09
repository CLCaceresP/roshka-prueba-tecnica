---
name: transactional-outbox
description: Publicar un mensaje desde un caso de uso de este repositorio sin dual-write, preservando el orden por pedido y la traza. Úsalo al agregar un comando o evento nuevo, al tocar OutboxPublisher o JdbcOutboxMensajes, o al revisar código que llama a KafkaTemplate.
---

# Outbox transaccional

El código de negocio **nunca** llama a `KafkaTemplate` (invariante 7). Escribe en el outbox dentro de su transacción, y `OutboxPublisher` publica después del commit ([ADR 0003](../../../docs/adr/0003-outbox-con-polling-y-shedlock.md)).

## Receta

### 1. En el caso de uso: `OutboxMensajes.agregar` dentro de la transacción

Copiado de `SagaReserva.enviar` (ms-pedidos):

```java
outbox.agregar(
    Topicos.INVENTARIO_COMANDOS,
    new SobreMensaje<>(
        UUID.randomUUID(), tipo, TiposMensaje.VERSION_ESQUEMA, reloj.instant(),
        idCorrelacion, idCausa, idPedido, carga));
```

### 2. Orden por agregado: toda escritura en el outbox incrementa la versión del agregado

Un único publisher que lee por `id` no basta: los ids siguen el orden de inserción, no el de commit. En ms-pedidos, `JpaPedidoRepository.actualizar` fuerza el incremento cuando el estado no cambió:

```java
var entidad = entityManager.find(PedidoEntity.class, pedido.id());
if (!entidad.copiarEstadoDe(pedido)) {
  entityManager.lock(entidad, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
}
```

En inventory cumple el mismo rol el lock de fila de `reservations`, tomado **antes** de escribir en el outbox.

### 3. La traza viaja con la fila

`JdbcOutboxMensajes` guarda el `traceparent` actual en `outbox.headers`; `OutboxPublisher` lo restaura al publicar:

```java
var span = propagador.extract(contexto, Map::get).name("outbox publish " + mensaje.topico()).start();
try (var alcance = tracer.withSpan(span)) {
  kafka.send(new ProducerRecord<>(mensaje.topico(), mensaje.idAgregado().toString(), mensaje.carga()))
      .get(properties.timeoutEnvio().toMillis(), TimeUnit.MILLISECONDS);
}
```

### 4. Publisher

- `@Scheduled` + `@SchedulerLock(name = "outbox-publisher")`: una instancia a la vez.
- `where published_at is null order by id`: por estado, nunca con marca de agua sobre `id`.
- Marca `published_at` **después** del ack; ante un fallo, corta el lote (no saltear filas).
- Key del record = `aggregate_id` (invariante 9).

## Checklist

- [ ] Ningún `KafkaTemplate` fuera de `adaptadores/salida/mensajeria`.
- [ ] `append` ocurre en la misma transacción que el cambio de negocio (test de rollback: `PedidoResilienciaIT`).
- [ ] La transacción que escribe en el outbox incrementa la versión del agregado o toma su lock de fila.
- [ ] El tópico nuevo y su `.DLT` se declaran en `MensajeriaConfiguration` con las mismas particiones.
- [ ] El contrato se documenta en `docs/asyncapi.yaml` y su record vive en `contrato-eventos`.
