# 006 · Plan técnico

## Componentes

| Elemento | Ubicación |
|---|---|
| Tracing (Micrometer + bridge OTel + exportador OTLP) | Dependencias y `application.yml` de cada servicio y del gateway. El starter exacto se confirma al fijar versiones (Fase 1). |
| Observation en Kafka | `spring.kafka.template.observation-enabled=true` y `spring.kafka.listener.observation-enabled=true` |
| Contexto de traza en el outbox | `adaptadores.salida.messaging`: al hacer `append` se serializa el contexto actual en `headers`; el publisher crea el span de envío con ese padre |
| `X-Correlation-Id` | Filtro en el gateway (genera) y filtro en cada servicio (acepta o genera y pone en MDC); se propaga en el envelope |
| MDC de `orderId`/`messageId` | Listeners y casos de uso, con try-with-resources sobre `MDC.putCloseable` |
| Métricas | Un componente por servicio que registra los medidores (`MetricasPedidos`, `MetricasInventario`); el dominio no conoce Micrometer: los casos de uso notifican por un puerto `out` |
| Health de Kafka | `IndicadorSaludKafka` en `adaptadores.salida.messaging`, incluido en el grupo `readiness` |
| Infra | `infra/otel-collector/config.yaml`, `infra/prometheus/prometheus.yml`, `infra/grafana/provisioning/{datasources,dashboards}/`, `infra/grafana/dashboards/pedidos-inventario.json` |

## Dashboard

Filas:

1. Pedidos por estado final y tasa de creación.
2. Reservas por resultado y motivo.
3. `saga_duration_seconds` p50/p95/p99.
4. Outbox pendiente y lag.
5. Duplicados y DLT por topic.
6. Espera de locks por tipo y resultado.

## Tests

| Criterio | Nivel |
|---|---|
| AC-02, AC-04 | it con un exportador de spans en memoria |
| AC-03 | it que captura la salida de log y la parsea como JSON |
| AC-05 | it contra `/actuator/prometheus` |
| AC-06 | it deteniendo el contenedor de Kafka |
| AC-01, AC-07 | verificación en compose documentada (script + captura) |
