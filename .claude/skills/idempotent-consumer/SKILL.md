---
name: idempotent-consumer
description: Implementar o revisar un consumidor de Kafka en este repositorio (inventario.comandos, inventario.eventos o un tópico nuevo) para que sea idempotente bajo at-least-once. Úsalo al agregar un listener, un tipo de mensaje o un caso de uso disparado por un mensaje.
---

# Consumidor idempotente

Todo mensaje puede llegar dos o más veces (REQ-26) y en cualquier orden fuera de la operación normal (spec 004). Un consumidor de este repo tiene **dos** defensas, y las dos son obligatorias ([ADR 0010](../../../docs/adr/0010-consumidor-idempotente-y-tombstone.md)):

1. **Duplicados exactos** (mismo `idMensaje`): `processed_messages` con `ON CONFLICT DO NOTHING`, en la misma transacción que el efecto.
2. **Duplicados lógicos** (otro `idMensaje`, mismo pedido): el efecto se decide por el **estado** de la fila del agregado, que además serializa (insert por PK o `SELECT ... FOR UPDATE`).

## Receta

### 1. Listener: traductor delgado, ack al final

Copiado de `ms-inventario/.../adaptadores/entrada/mensajeria/ComandosInventarioListener.java`:

```java
@KafkaListener(topics = Topicos.INVENTARIO_COMANDOS)
void alComando(ConsumerRecord<String, String> registro, Acknowledgment ack) {
  var valor = registro.value();
  var vistaPrevia = lector.vistaPrevia(valor);   // valida versionEsquema: si falla, MensajeMalformado -> DLT
  try (var idMensaje = MDC.putCloseable("messageId", vistaPrevia.idMensaje());
      var idPedido = MDC.putCloseable("orderId", vistaPrevia.idPedido());
      var idCorrelacion = MDC.putCloseable("correlationId", vistaPrevia.idCorrelacion())) {
    var resultado = switch (vistaPrevia.tipo()) {
      case TiposMensaje.RESERVAR_STOCK -> reservarStock.manejar(lector.leer(valor, ReservarStock.class));
      ...
      default -> throw new MensajeMalformado("Tipo inesperado en " + registro.topic(), null);
    };
    if (resultado == ResultadoManejo.DUPLICADO) {
      medidores.counter("messages.duplicated", "type", vistaPrevia.tipo()).increment();
    }
    ack.acknowledge();                            // solo después del commit del caso de uso
  }
}
```

### 2. Caso de uso: dedupe primero, en la misma transacción

Copiado de `ReservarStockHandler`:

```java
@Transactional
public ResultadoManejo manejar(SobreMensaje<ReservarStock> comando) {
  if (!mensajesProcesados.marcarProcesado(comando.idMensaje())) {
    return ResultadoManejo.DUPLICADO;
  }
  var reserva = reservas.buscarParaActualizar(idPedido)          // serializa por pedido
      .orElseGet(() -> reservarNueva(idPedido, comando.carga().lineas()));
  outbox.agregar(Topicos.INVENTARIO_EVENTOS, respuesta(comando, reserva.respuestaAReservar()));
  return ResultadoManejo.APLICADO;
}
```

### 3. `markProcessed` con `ON CONFLICT`, nunca capturando la excepción de PK

Copiado de `JdbcMensajesProcesados`: en Postgres un error aborta toda la transacción.

```sql
insert into processed_messages (message_id, processed_at) values (:id, :at)
on conflict (message_id) do nothing
```

## Checklist

- [ ] Dedupe y efecto en **una** transacción (`@Transactional` en el caso de uso, no en el listener).
- [ ] El efecto depende del estado del agregado, no solo del `idMensaje`: dos `LiberarStock` distintos devuelven stock una sola vez.
- [ ] Ack manual después del commit; nunca auto-commit.
- [ ] Un mensaje que no cumple el contrato lanza `MensajeMalformado` (no reintentable → DLT).
- [ ] La carrera de inserts concurrentes del mismo agregado (`DuplicateKeyException`) se deja **reintentable**.
- [ ] Respuestas al outbox con `idCausa` = `idMensaje` del comando y el mismo `idCorrelacion`.
- [ ] Tests: entrega 3 veces del mismo mensaje (IT) y secuencias aleatorias con semilla (`ReservaSecuenciasAleatoriasTest`).
