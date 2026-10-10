# 008 · Plan

## Componentes

| Pieza | Dónde | Qué |
|---|---|---|
| `RegistroSolicitudesFilter` | `adaptadores/entrada/rest` de `ms-pedidos` y `ms-inventario` | `OncePerRequestFilter` con orden `HIGHEST_PRECEDENCE + 2`: dentro del filtro de correlación (MDC `correlationId`) y de la observación HTTP de Spring (traza activa). Al terminar escribe la línea de la solicitud. La ruta sale del atributo `BEST_MATCHING_PATTERN_ATTRIBUTE`. |
| `RegistroSolicitudes` (filtro global) | `gateway-pedidos-inventario` | `GlobalFilter` reactivo que mide la duración y, al completar, escribe la misma línea con `routeId` y destino. Toma el `traceId` del contexto de la observación HTTP de Spring (`ServerRequestObservationContext`) al entrar, porque el contexto reactivo no mantiene el MDC ni `Tracer.currentSpan()`. |
| Eventos de negocio | `SagaReserva` (todos los eventos del pedido pasan por `despachar`), manejadores de `ms-inventario` y `OutboxPublisher` | `LoggerFactory.getLogger` de SLF4J con `addKeyValue`. `AtributosLog.conPedido` evita repetir `orderId` cuando el MDC ya lo trae: la consola JSON falla si una clave se escribe dos veces. El dominio no registra logs. |
| `management.opentelemetry.resource-attributes` | `application.yml` | `service.namespace`, `service.version` (`@project.version@`), `deployment.environment.name`, `host.name` y `process.pid`. |
| `logging.structured.json.add` | `application.yml` | `application`, `group`, `environment` y `machineName` en la consola JSON. |
| `logging.level.org.apache.kafka` | `application.yml` | `WARN`. |
| Variable `ENTORNO` | `docker-compose.yml` | Opcional; por defecto `local`. |

## Modelo de datos y mensajes

Ninguno. No hay migraciones ni cambios en el contrato de mensajes.

## ADRs

- [0017](../../docs/adr/0017-logs-de-solicitudes-y-enrichers.md) · cómo se registran las solicitudes y se agregan los enrichers.
- [0016](../../docs/adr/0016-logs-en-seq-via-otlp.md) · destino de los logs.

## Estrategia de tests

| Criterio | Nivel | Cómo |
|---|---|---|
| 008-AC-01, 008-AC-02, 008-AC-03, 008-AC-05 | it (`ms-pedidos`, `ms-inventario`) | `LogRecordExporter` en memoria; solicitudes con `MockMvcTester`. |
| 008-AC-04 | it (gateway) | Backend simulado y exportador en memoria. |
| 008-AC-06 | it | Sobre los flujos existentes por Kafka, con el exportador en memoria. |
| 008-AC-07 | compose | Verificación manual contra la API de Seq, descrita en el README. |
