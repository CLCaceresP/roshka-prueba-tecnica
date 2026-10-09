# 0006 · Reintentos bloqueantes con backoff exponencial y DLT

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-02, REQ-14, REQ-18, REQ-26, REQ-27

## Contexto y problema

Un consumidor puede fallar por causas **temporales** (base momentáneamente no disponible, lock no obtenido, conflicto de versión, timeout de red) o **permanentes** (mensaje que no deserializa, payload inválido, violación del contrato). El README debe explicar la estrategia de reintentos y el manejo de mensajes que no pueden procesarse (REQ-18). Los reintentos no deben romper el orden por pedido ([0002](0002-kafka-como-sistema-de-mensajeria.md)).

## Factores de decisión

- Preservar el orden dentro de cada partición (y por lo tanto por pedido).
- No reintentar lo que nunca va a funcionar.
- Que ningún mensaje se pierda en silencio: todo lo descartado queda en la DLT, con su causa y una métrica.

## Opciones consideradas

1. **Reintentos no bloqueantes con `@RetryableTopic`.** El mensaje fallido se mueve a `topic-retry-N` y la partición sigue avanzando. Mientras `ReservarStock` del pedido X espera en el topic de retry, su `LiberarStock` puede procesarse antes en el topic principal. **Rompe el orden por pedido**, que es justo lo que el diseño necesita.
2. **Reintentos bloqueantes con `DefaultErrorHandler` + `ExponentialBackOff`.** El consumidor reintenta el mismo offset y no avanza la partición hasta resolverlo o agotar el backoff.
3. **Sin reintentos, directo a la DLT.** Convierte cada error temporal en intervención manual.

## Decisión

**Opción 2.**

- `DefaultErrorHandler` con `ExponentialBackOff`. Valores en `application.yml` (`mensajeria.reintentos.intervalo-inicial`, `multiplier`, `max-interval`, `max-elapsed-time`). El tiempo total debe quedar **por debajo de `max.poll.interval.ms`** para que el broker no expulse al consumidor durante el backoff.
- **Clasificación de errores con Strategy** (`ErrorClassification`): decide si una excepción es reintentable.
  - No reintentables, directo a la DLT sin backoff: errores de deserialización y conversión, validación del contrato, `versionEsquema` desconocida, transición de estado que el dominio declara imposible (indica un bug o un contrato roto, no un evento tardío).
  - Reintentables: errores transitorios de acceso a datos, lock no obtenido, conflicto de versión optimista, timeouts.
  - Los eventos tardíos o duplicados **no son errores**: se manejan por estado (spec 004) y se hace ack.
- Al agotar los reintentos o ante un error no reintentable, `DeadLetterPublishingRecoverer` publica en `<topico>.DLT`, en la **misma partición**, con los headers estándar de causa (excepción, mensaje, stacktrace, topic/partición/offset original). Por cada envío: `messages_dlt_total{topic}` y un log estructurado con `idMensaje`, `orderId` y la causa.
- La DLT se reprocesa manualmente ([procedimiento operativo](../dlt-recovery.md)). Es seguro porque los consumidores son idempotentes por `idMensaje` **y** por estado. Desde [0014](0014-renovacion-outbox-y-recuperacion-dlt.md), el contador y el log de publicación exitosa ocurren después del ack del broker; si publicar falla, se propaga el error para reintentar.
- Resilience4j solo donde haya llamadas síncronas reales entre servicios. En el diseño actual no hay ninguna.

## Consecuencias

- Positivas: el orden por pedido se mantiene aun con fallos; ningún mensaje se descarta sin rastro; la frontera entre temporal y permanente es explícita y se prueba.
- Negativas: **bloqueo de cabeza de línea**. Un mensaje en reintento detiene los demás pedidos de su partición durante, como máximo, `max-elapsed-time`. Se acepta porque el tiempo es acotado y configurable, y la alternativa rompe la corrección.
- Riesgos: un error permanente mal clasificado como temporal retrasa la partición hasta agotar el backoff. Ocurre una vez, no indefinidamente, y la métrica de la DLT lo hace visible.

## Cómo se verifica

- Test de integración: una excepción reintentable que se recupera al tercer intento → el mensaje se procesa una sola vez y en orden respecto al siguiente del mismo pedido.
- Test de integración: un payload no deserializable va a `<topico>.DLT` sin reintentos, con headers de causa, e incrementa la métrica.
- Tests unitarios de `ErrorClassification`.

## Notas de implementación

- Spring Kafka 4 cambió el sufijo por defecto de `DeadLetterPublishingRecoverer` a `-dlt`. El destino se fija explícitamente en `<topico>.DLT` y la misma partición, que son los topics que crea cada servicio. El test de resiliencia lo verifica.
- El header `kafka_dlt-exception-fqcn` registra el wrapper de Spring (`ListenerExecutionFailedException`); la causa real está en `kafka_dlt-exception-cause-fqcn`.

## Referencias

- Spring for Apache Kafka, "Handling Exceptions", "DefaultErrorHandler", "Publishing Dead-letter Records", "Non-Blocking Retries".
