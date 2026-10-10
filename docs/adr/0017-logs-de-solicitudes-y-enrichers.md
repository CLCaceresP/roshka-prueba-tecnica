# 0017 · Logs de solicitudes con filtro propio y enrichers como atributos de recurso

- Estado: Aceptado
- Fecha: 2026-10-10
- Requisitos: BON-03, REQ-18

## Contexto y problema

Con los logs ya en Seq ([ADR 0016](0016-logs-en-seq-via-otlp.md)), faltan dos cosas para seguir un pedido de punta a punta: una línea por solicitud HTTP y *enrichers* (datos fijos de la aplicación, la máquina y el proceso en cada log), como hace el proyecto .NET `spi-template` con Serilog (`UseSerilogRequestLogging`, `WithMachineName`, `WithProcessId`, `WithThreadId`, propiedades `ApplicationName` y `Group`).

Los servicios son Spring MVC (servlet) y el gateway es Spring Cloud Gateway (WebFlux), que se comportan distinto con el contexto de la solicitud.

## Factores de decisión

- Una línea por solicitud con ruta como plantilla (baja cardinalidad), estado, duración, IP y user agent.
- Que el `traceId` y el `correlationId` estén en esa línea.
- No registrar cuerpos ni datos sensibles.
- Que los enrichers se configuren en `application.yml` y no con código por servicio.
- Funcionar igual en servlet y en WebFlux.

## Opciones consideradas para la línea de solicitud

1. **Un filtro propio por pila (elegida).** `OncePerRequestFilter` en los servicios y `GlobalFilter` en el gateway. Control total del mensaje, del nivel según el estado y de los atributos.
2. **`ObservationHandler` sobre `http.server.requests`.** Un solo mecanismo para ambas pilas, pero el handler corre al cerrar la observación, cuando el MDC y la traza pueden ya no estar activos, y obliga a leer el request desde un contexto distinto en cada pila.
3. **`CommonsRequestLoggingFilter`.** Registra en DEBUG, con cuerpo opcional y sin duración ni estado de la respuesta.
4. **Access log del servidor.** Solo escribe a archivo/consola en su propio formato y no pasa por el appender de OpenTelemetry.

## Opciones consideradas para los enrichers

1. **Atributos de recurso de OpenTelemetry vía `management.opentelemetry.resource-attributes` (elegida)**, más `logging.structured.json.add` para la consola. Se declaran una vez por servicio en `application.yml` y viajan con cada registro exportado.
2. **Un `LogRecordProcessor` propio que agregue atributos a cada registro.** Más flexible, pero es código nuevo para datos que son constantes del proceso.
3. **Poner los datos en el MDC al arrancar.** Obliga a mantener el MDC en cada hilo y no cubre los hilos de Kafka ni los programados.

## Decisión

- **Solicitudes:** `RegistroSolicitudesFilter` en `ms-pedidos` y `ms-inventario`, con orden `HIGHEST_PRECEDENCE + 2` (después del filtro de correlación y de la observación HTTP, para tener MDC y traza). En el gateway, un `GlobalFilter` que escribe al completar la respuesta y toma el `traceId` del contexto de la observación HTTP de Spring al entrar. Mensaje `Solicitud HTTP procesada` con `method`, `path` (plantilla), `status`, `durationMs`, `clientIp`, `host`, `userAgent` y `correlationId`; el gateway agrega `routeId` y destino. INFO por debajo de 400, WARN para 4xx y ERROR para 5xx. `/actuator/**`, `/swagger-ui/**` y `/v3/api-docs/**` van en DEBUG.
- **Enrichers:** `service.namespace = pedidos-inventario` (el *Group* de Serilog), `service.version = @project.version@`, `deployment.environment.name = ${ENTORNO:local}`, `host.name = ${HOSTNAME:${COMPUTERNAME:desconocido}}` y `process.pid = ${PID:0}` como atributos de recurso; `application`, `group`, `environment` y `machineName` en la consola JSON. `thread.name` ya lo agrega el appender de OpenTelemetry.
- **Eventos de negocio** en INFO desde los casos de uso, con SLF4J y `addKeyValue`. El dominio sigue sin loguear.
- **Niveles:** `org.apache.kafka` en WARN.
- Los nombres de los atributos están en inglés: son contrato de observabilidad, igual que las claves del MDC ([ADR 0015](0015-codigo-en-espanol-y-contratos-externos-en-ingles.md)). Los mensajes, en español.

## Consecuencias

**Positivas**
- Se puede seguir un `correlationId` desde el gateway hasta el último evento de negocio, y filtrar por servicio, ruta, estado o duración.
- Los enrichers no requieren código por servicio ni un procesador propio.

**Negativas y riesgos**
- Más volumen de logs: una línea por solicitud más los eventos de negocio. Mitigación: las sondas van en DEBUG y Kafka en WARN.
- En el gateway el MDC no atraviesa el flujo reactivo, así que `correlationId` y `traceId` van como atributos de la línea y no en el MDC. Otros logs del gateway no los llevan.
- `process.pid` y `host.name` dependen de variables de entorno (`PID`, `HOSTNAME`/`COMPUTERNAME`); si no existen, valen `0` y `desconocido`.
- La IP del cliente que ve el servicio es la del gateway, no la del cliente final; el gateway sí registra la del cliente.
- No se registran cuerpos, así que un problema de contenido no se ve en los logs.

## Cómo se verifica

- Los IT `LogsSolicitudesIT` de cada módulo (008-AC-01 a 008-AC-06) con un exportador de logs en memoria.
- 008-AC-07 en el stack de compose contra la API de Seq, descrito en el README.

## Referencias

- [Spec 008](../../specs/008-trazabilidad-de-logs/spec.md) · [Spec 007](../../specs/007-logs-en-seq/spec.md)
- Proyecto de referencia: `spi-template` (.NET, Serilog).
