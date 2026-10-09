# 006 · Observabilidad y trazabilidad

Estado: Aprobada
Requisitos: BON-03, REQ-02, REQ-18, REQ-28

## Contexto

En un flujo asíncrono con varias instancias, sin trazabilidad es imposible responder "¿qué pasó con el pedido X?". El objetivo es que un pedido pueda seguirse de punta a punta en **una sola traza**: gateway → order → Kafka → inventory → Kafka → order. También se busca que los logs y las métricas permitan diagnosticar duplicados, reintentos, DLT y carreras sin entrar a las bases.

## Reglas

### Trazas

- **RN-01** Micrometer Tracing con bridge OpenTelemetry, exportando por OTLP al OpenTelemetry Collector, que reenvía a Jaeger. Muestreo al 100 % en compose (`management.tracing.sampling.probability`).
- **RN-02** Propagación W3C (`traceparent`) en HTTP y en headers de Kafka, con observation habilitada en `KafkaTemplate` y en los listeners.
- **RN-03** **El outbox no corta la traza.** Al escribir la fila del outbox se guarda el contexto de traza actual (`traceparent`) en `outbox.headers`. El publisher lo restaura al publicar, de modo que el span de envío es hijo del span que originó el mensaje y no del job de polling.
- **RN-04** El timeout de la saga y la purga son jobs: empiezan su propia traza. Si emiten mensajes, cada mensaje enlaza con esa traza.

### Correlación y logs

- **RN-05** El gateway genera `X-Correlation-Id` si no viene y lo devuelve en la respuesta. Los servicios lo aceptan (o lo generan si se los llama directamente), lo propagan en el envelope (`correlationId`) y lo devuelven.
- **RN-06** Logs en JSON con el structured logging nativo de Spring Boot. Cada línea relativa a un pedido o mensaje incluye en MDC `traceId`, `spanId`, `correlationId`, `orderId` y `messageId`, según corresponda.

### Métricas (Micrometer → Prometheus)

| Métrica | Tipo | Tags | Dónde |
|---|---|---|---|
| `orders_placed_total` | counter | — | ms-pedidos |
| `orders_by_final_status_total` | counter | `status`, `reason` | order, al entrar a un estado terminal |
| `stock_reservations_total` | counter | `result` = `reserved` / `rejected`, `reason` | ms-inventario |
| `messages_duplicated_total` | counter | `service`, `type` | ambos |
| `messages_dlt_total` | counter | `topic` | ambos |
| `outbox_pending_messages` | gauge | `service` | ambos |
| `outbox_publish_lag_seconds` | timer/histograma | `service` | ambos, `published_at - created_at` |
| `saga_duration_seconds` | histograma | `status` | order, `saga_started_at` → estado terminal |
| `distributed_lock_wait_seconds` | timer/histograma | `lock` = `idem` / `order`, `outcome` = `acquired` / `timeout` | ambos |

Los nombres se definen en Micrometer con puntos (`orders.placed`) y Prometheus los expone con el sufijo correspondiente (`orders_placed_total`). Los tests verifican el nombre expuesto. El prompt pedía `orders_created_total`, pero OpenMetrics reserva el sufijo `_created` para el timestamp de creación de cada counter y el cliente de Prometheus lo expone como `orders_total`; por eso la métrica se llama `orders_placed_total`.

### Health

- **RN-07** **Readiness** incluye la base, Kafka y Redis. Una instancia que no puede alcanzarlos deja de recibir tráfico.
- **RN-08** **Liveness** solo refleja el estado interno de la aplicación, sin dependencias externas. Ver P-01.
- **RN-09** Spring Boot no trae un health indicator de Kafka; se implementa uno con `AdminClient#describeCluster` y un timeout configurable.

### Dashboards

- **RN-10** Grafana se provisiona desde el repo (`infra/grafana/provisioning/` + un dashboard JSON) con datasources de Prometheus y Jaeger. No requiere ninguna configuración manual.

## Criterios de aceptación

### 006-AC-01 · Traza de punta a punta (BON-03)
- Given el stack levantado en compose
- When se crea un pedido por el gateway y llega a `CONFIRMED`
- Then en Jaeger existe una sola traza con spans de gateway, order (HTTP), order (publish), inventory (consume y publish) y order (consume)
- And se documenta en el README con la consulta a la API de Jaeger (`/api/v3/traces`), verificada el 2026-10-09 (10 spans en 3 servicios), y a nivel servicio con ITs que verifican que el `traceparent` del record de Kafka pertenece a la traza del request o del comando

### 006-AC-02 · El outbox preserva el contexto (BON-03)
- Given un caso de uso que escribe en el outbox dentro de un span S
- When el publisher envía la fila
- Then el record de Kafka lleva un `traceparent` cuyo trace id es el de S

### 006-AC-03 · Logs estructurados (BON-03)
- When se procesa un mensaje de un pedido
- Then la línea de log es JSON válido y contiene `traceId`, `spanId`, `correlationId`, `orderId` y `messageId`

### 006-AC-04 · Correlation id (BON-03)
- When se hace un request sin `X-Correlation-Id`
- Then la respuesta trae uno generado, y los mensajes y logs derivados llevan el mismo valor
- And si el request lo trae, se respeta

### 006-AC-05 · Métricas expuestas (BON-03, REQ-18)
- When se ejecuta un camino feliz, un rechazo, un duplicado y un mensaje a la DLT
- Then `/actuator/prometheus` expone todas las métricas de la tabla con los incrementos esperados

### 006-AC-06 · Health (REQ-02)
- Given Kafka detenido
- When se consulta `/actuator/health/readiness` y `/actuator/health/liveness`
- Then readiness responde `DOWN` (`503`) y liveness responde `UP`

### 006-AC-07 · Dashboard provisionado (BON-03, REQ-28)
- Given `docker compose up` sobre un volumen limpio
- When se consulta la API de Grafana
- Then el dashboard del sistema existe y sus paneles consultan métricas que existen en Prometheus

## Casos borde

- El Collector no está disponible: la aplicación no falla. Los spans se descartan tras un buffer acotado.

## Fuera de alcance

- Alertas (Alertmanager) y SLOs.
- Agregación centralizada de logs (Loki/ELK). Los logs se ven con `docker compose logs`.

## Preguntas resueltas

- **P-01 · Liveness con dependencias.** Decidido el 2026-10-09: las dependencias van solo en readiness. El prompt pide readiness y liveness "que incluyan Kafka, DB y Redis". Incluir dependencias externas en liveness es contraproducente: si Kafka cae, el orquestador reinicia todas las réplicas en cascada sin arreglar nada. **Recomendado:** las dependencias solo en readiness (RN-07, RN-08), que es la práctica que documenta Spring Boot.
