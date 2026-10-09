# 0014 · Renovación del outbox y recuperación de DLT

- Estado: Aceptado
- Fecha: 2026-10-10
- Requisitos: REQ-06, REQ-12, REQ-18, REQ-26, REQ-27

## Contexto

Un lote de 100 filas con envíos lentos puede superar el lease de 30 s del publisher. Otra instancia podría adquirir el lock mientras la primera sigue publicando. Además, el contador y el log de DLT se emitían antes de saber si Kafka aceptó el registro.

La DLT tiene reproceso manual. Una cancelación de un pedido confirmado puede conservar stock reservado si su `LiberarStock` agota los reintentos. El timeout de la saga solo revisa `PENDING`, por lo que no reconcilia esas cancelaciones.

## Opciones consideradas

1. Aumentar el lease para cubrir el lote entero: retrasa el reemplazo de una instancia que cae y depende de un cálculo del peor caso del lote.
2. Limitar el tiempo del lote: reduce throughput y sigue necesitando acotar cada envío.
3. Renovar el lock antes de cada fila con `LockExtender`.
4. Usar `KeepAliveLockProvider`: agrega un scheduler de renovación en segundo plano.

## Decisión

Elegir la opción 3 con el proveedor JDBC existente. El job público y síncrono renueva su lock antes de cada envío. Si el lock ya venció o no se puede extender, la excepción detiene el lote antes del próximo envío; las filas restantes siguen pendientes. La extensión no agrega una dependencia ni un hilo de mantenimiento.

Acotar también el bloqueo síncrono de `KafkaProducer.send` con `max.block.ms=10000`. El lease de 30 s cubre ese bloqueo, la espera del futuro (`send-timeout=10s`) y un margen para marcar la fila en la base. Si se cambian estos valores, debe conservarse esa relación. El lease limita una fila; ya no se calcula como `batch-size × send-timeout`.

Configurar explícitamente `DeadLetterPublishingRecoverer.setFailIfSendResultIsError(true)` y ejecutar la recuperación antes de aumentar el contador o escribir el log de publicación exitosa. Un fallo de publicación no se cuenta como entrega y se propaga al error handler para reintentar.

Mantener el reproceso manual dentro del alcance de la prueba y documentar sus efectos y pasos en [dlt-recovery.md](../dlt-recovery.md). Se reenvía un registro seleccionado al topic original con su key y envelope intactos. No se reinician offsets ni se modifica el stock por SQL.

## Consecuencias

- Los lotes pueden superar el lease inicial sin permitir otro publisher mientras se renueve correctamente.
- Un proceso pausado o una operación individual que supere el lease todavía pueden producir duplicados. La idempotencia sigue siendo necesaria.
- La métrica de DLT representa publicaciones confirmadas, no intentos fallidos. Una caída después del ack y antes de registrar la métrica puede subcontar; la métrica no forma una transacción con Kafka.
- Las liberaciones en DLT requieren intervención manual. Una reconciliación automática queda como mejora posterior.

## Verificación

- `OutboxPublisherIT` en ambos servicios: lote lento que excede el lease inicial, competidor sobre Postgres real, orden y cantidad de mensajes recibidos en Kafka, pérdida del lease y recuperación tras un envío fallido.
- `ManejoErroresKafkaTest` en ambos servicios: primer envío DLT rechazado, contador en cero; segundo envío confirmado, contador en uno.
- `InventarioResilienciaIT`: liberación que agota reintentos, stock retenido, corrección de la causa y dos reenvíos del registro original; una sola liberación y una sola respuesta.
- Spec 003: criterios 003-AC-14, 003-AC-15 y 003-AC-16.

## Referencias

- [ShedLock: extensión del lock](https://github.com/lukas-krecan/ShedLock#extending-the-lock).
- [Spring Kafka: recuperación y publicación en DLT](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html).
