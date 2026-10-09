# 0002 · Kafka como sistema de mensajería, at-least-once con key = orderId

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-02, REQ-06, REQ-07, REQ-14, REQ-17, REQ-18, REQ-26, REQ-27

## Contexto y problema

Order e Inventory se comunican de forma asíncrona (REQ-17). El enunciado obliga a asumir entrega at-least-once (REQ-26) y a explicar en el README el motivo de la elección, la garantía de entrega, los reintentos, el manejo de mensajes no procesables y el orden (REQ-18).

El orden importa para cada pedido: `ReservarStock` y luego `LiberarStock` del mismo pedido deben procesarse en ese orden para no liberar algo que aún no se reservó. Entre pedidos distintos el orden es irrelevante.

## Factores de decisión

- Orden garantizado por pedido con varias instancias consumidoras (REQ-02).
- Reprocesamiento: poder releer mensajes tras un bug o desde la DLT.
- Integración madura con Spring (Spring Kafka, observation, DLT).
- Operable en Docker Desktop en una laptop.

## Opciones consideradas

1. **Kafka (KRaft).** Orden por partición; la key `orderId` lleva todos los mensajes de un pedido a la misma partición, consumida por una sola instancia del grupo. Retención y replay nativos.
2. **RabbitMQ.** Buen ruteo y DLX nativos, pero el orden con varios consumidores competidores en una cola no está garantizado. Habría que usar consistent-hash exchange o single active consumer por cola, lo que agrega topología. El replay requiere streams.
3. **Redis Streams.** Ya está en el stack, pero los consumer groups no ofrecen orden por clave entre consumidores y el ecosistema de reintentos y DLT es manual.

## Decisión

**Kafka en modo KRaft**, con estas garantías:

- **Entrega at-least-once.** Un mensaje puede llegar más de una vez. Todo consumidor es idempotente (invariante 8) y los efectos son idempotentes por estado (invariantes 4–6).
- **Producer:** `enable.idempotence=true` y `acks=all`. Evita duplicados por reintentos del propio producer y pérdidas ante la caída del líder.
- **Consumer:** `enable.auto.commit=false`, ack manual **después** del commit de la transacción de la base (invariante 10). Si el proceso cae entre el commit y el ack, el mensaje se reentrega y se descarta como duplicado.
- **Key = `orderId`** en todo mensaje (invariante 9). Topics `inventario.comandos` e `inventario.eventos`, cada uno con 3 particiones en compose para que dos réplicas reciban trabajo.
- **No se usan transacciones de Kafka (exactly-once).** Su garantía cubre lectura-proceso-escritura entre topics de Kafka, pero el efecto aquí ocurre en Postgres. Igual se necesitarían el consumidor idempotente y el outbox ([0003](0003-outbox-con-polling-y-shedlock.md)); agregarlas solo suma complejidad.
- Reintentos y mensajes no procesables: ver [0006](0006-reintentos-bloqueantes-y-dlt.md).

## Consecuencias

- Positivas: orden por pedido sin coordinación adicional, replay para diagnóstico y reprocesamiento, buen soporte de observabilidad (propagación de `traceparent` en headers).
- Negativas: Kafka pesa más que Rabbit en una laptop. En compose se usa un solo broker con factor de replicación 1, que **no es tolerante a fallos** y se documenta como limitación.
- Riesgos: un mensaje que no se puede procesar bloquea su partición mientras dura el backoff (aceptado en 0006). Un rebalanceo puede reentregar mensajes ya procesados, cubierto por la idempotencia.

## Cómo se verifica

- Tests de integración con Testcontainers Kafka: duplicados (escenario 3) y dos consumidores (escenario 5) de la suite concurrente.
- Test que verifica que todo mensaje publicado lleva key = `idAgregado`.

## Referencias

- Spring for Apache Kafka, "Committing Offsets" y "Observation".
- Kafka idempotent producer (KIP-98).
