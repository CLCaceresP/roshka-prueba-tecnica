# 008 · Trazabilidad de logs: solicitudes HTTP, eventos de negocio y enrichers

Estado: Aprobada
Requisitos: BON-03, REQ-18

## Contexto

La [spec 007](../007-logs-en-seq/spec.md) lleva los logs a Seq, pero hoy los servicios casi no escriben nada sobre lo que pasa: no hay una línea por solicitud HTTP y los eventos de negocio (pedido colocado, confirmado, cancelado…) no dejan rastro. Seguir un pedido desde el gateway hasta inventario obliga a adivinar. Esta spec toma como modelo el proyecto .NET `spi-template` (Serilog): una línea por solicitud con IP, host, ruta, método y user agent; *enrichers* globales (máquina, proceso, hilo, correlation id); propiedades fijas (`ApplicationName`, `Group`) y niveles mínimos con *overrides* por librería.

Decisiones en el [ADR 0017](../../docs/adr/0017-logs-de-solicitudes-y-enrichers.md).

## Reglas de negocio

- **RN-01** Cada servicio escribe **una línea por solicitud HTTP** al terminar: mensaje `Solicitud HTTP procesada` con `method`, `path` (la plantilla de la ruta, p. ej. `/orders/{orderId}`), `status`, `durationMs`, `clientIp`, `host` y `userAgent`. El gateway agrega `routeId` y el destino. Nivel: INFO para estados menores a 400, WARN para 4xx y ERROR para 5xx.
- **RN-02** Las solicitudes a `/actuator/**`, `/swagger-ui/**` y `/v3/api-docs/**` (sondas y documentación) se registran en DEBUG para no inundar Seq.
- **RN-03** Todo log lleva `correlationId` (MDC en los servicios; atributo de la línea en el gateway) y, dentro de una solicitud, el `traceId` y el `spanId` de la traza.
- **RN-04** Los logs exportados llevan como atributos de recurso: `service.name` (nombre de la aplicación), `service.namespace` (grupo, `pedidos-inventario`), `service.version`, `deployment.environment.name` (variable `ENTORNO`, por defecto `local`), `host.name` y `process.pid`. Cada registro agrega el hilo (`thread.name`). La consola JSON incluye `application`, `group`, `environment` y `machineName` (el `host` de la línea de solicitud es el de la petición, no el de la máquina).
- **RN-05** Los eventos de negocio se registran en INFO con sus identificadores como atributos (`orderId`, `productId`, `messageId`):
  - `ms-pedidos`: `Pedido colocado`, `Pedido confirmado`, `Pedido rechazado` (con el motivo, incluido `TIMEOUT`), `Pedido cancelado`, `Liberación de stock solicitada` (con la causa) y `Mensaje publicado desde el outbox`.
  - `ms-inventario`: `Producto registrado`, `Reserva decidida` (reservada o rechazada, con el motivo), `Stock liberado` (con el desenlace) y `Mensaje publicado desde el outbox`.
  - Los mensajes duplicados los registra el listener con `result=DUPLICADO` (spec 007); los bloqueos no obtenidos y los reintentos del outbox ya salen en WARN.
- **RN-06** Los niveles mínimos se configuran en `application.yml`: INFO por defecto y `org.apache.kafka` en WARN, igual que los *overrides* de Serilog.
- **RN-07** No hay llamadas HTTP salientes entre servicios (solo Kafka, que lleva el `idCorrelacion` en el sobre) y el gateway ya reenvía `X-Correlation-Id`; no se agrega propagación de headers.

## Criterios de aceptación

### 008-AC-01 · Una línea por solicitud con su contexto (BON-03)
- Given un servicio con un exportador de logs OTLP configurado
- When recibe `GET` de un recurso con `X-Correlation-Id`
- Then el exportador recibe `Solicitud HTTP procesada` con `method`, `path` (plantilla), `status`, `durationMs`, `clientIp`, `userAgent`, `correlationId` y un `traceId` válido

### 008-AC-02 · El nivel depende del estado (BON-03)
- Given un servicio con exportador de logs
- When responde 404 a una solicitud
- Then la línea de la solicitud sale en nivel WARN

### 008-AC-03 · Las sondas no inundan los logs (BON-03)
- Given un servicio con exportador de logs
- When recibe `GET /actuator/health/readiness`
- Then su línea sale en DEBUG, es decir, no aparece entre los logs INFO

### 008-AC-04 · El gateway registra la solicitud y su ruta (BON-03)
- Given el gateway con un exportador de logs y un backend simulado
- When enruta una solicitud con `X-Correlation-Id`
- Then registra `Solicitud HTTP procesada` con `method`, `path`, `status`, `routeId`, `correlationId` y `durationMs`

### 008-AC-05 · Los enrichers acompañan a cada log (BON-03)
- Given un servicio con exportador de logs
- When registra cualquier log
- Then el recurso exportado trae `service.name`, `service.namespace`, `service.version`, `deployment.environment.name`, `host.name` y `process.pid`, y el registro trae `thread.name`

### 008-AC-06 · Los eventos de negocio dejan rastro (BON-03)
- Given `ms-pedidos` con exportador de logs
- When se coloca un pedido y llega `StockReservado`
- Then se exportan `Pedido colocado`, `Mensaje publicado desde el outbox` (con el tópico y el `messageId`) y `Pedido confirmado`, todos con `orderId`
- Given un pedido pendiente
- When se cancela
- Then se exportan `Pedido cancelado` y `Liberación de stock solicitada` con la causa `CANCELADO_POR_CLIENTE`
- Given `ms-inventario` con exportador de logs
- When llega un `ReservarStock`
- Then se exporta `Reserva decidida` con `orderId` y el resultado (`RESERVED` o `REJECTED`)
- Given un producto nuevo
- When se registra
- Then se exporta `Producto registrado` con `productId`
- Given una reserva
- When llega un `LiberarStock`
- Then se exporta `Stock liberado` con `orderId` y el desenlace

### 008-AC-07 · Todo el recorrido se sigue en Seq (BON-03, REQ-18)
- Given el stack de compose levantado
- When se crea un pedido por el gateway con un `X-Correlation-Id`
- Then en Seq, filtrando por ese `correlationId`, aparecen en orden: la solicitud del gateway, la del servicio, `Pedido colocado`, `Mensaje publicado desde el outbox`, `Comando de inventario manejado`, `Reserva decidida`, `Respuesta de inventario manejada` y `Pedido confirmado`

## Casos borde
- Si la solicitud falla con una excepción no controlada, la línea sale en ERROR con el estado 500.
- Los cuerpos de las solicitudes y las respuestas **no** se registran (pueden traer datos que no deben ir a los logs).
- `userAgent` y `clientIp` pueden faltar; se omiten sin error.

## Fuera de alcance
- Registrar cuerpos, headers completos o el SQL de Hibernate.
- Logs de acceso del servidor (Tomcat/Netty) y su rotación.
- Dashboards y alertas sobre logs.

## Preguntas abiertas
Ninguna.
