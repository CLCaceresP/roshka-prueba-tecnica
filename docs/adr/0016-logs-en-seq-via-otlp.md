# 0016 · Logs en Seq por OTLP a través del OTel Collector

- Estado: Aceptado
- Fecha: 2026-10-10
- Requisitos: BON-03, REQ-18

## Contexto y problema

Los tres servicios escriben logs JSON por consola. Seguir un pedido a través de `gateway-pedidos-inventario`, `ms-pedidos` y `ms-inventario` obliga a abrir tres salidas de `docker compose logs` y cruzarlas a mano. El responsable del proyecto pidió que todos los logs se registren en Seq.

Las trazas ya viajan por OTLP de cada servicio al OTel Collector y de ahí a Jaeger ([spec 006](../../specs/006-observabilidad/spec.md)). Spring Boot 4 puede exportar logs por OTLP con `management.opentelemetry.logging.export.otlp.endpoint`, pero no instala el appender de Logback: hay que agregarlo.

## Factores de decisión

- Una caída del destino de logs no debe afectar a los servicios.
- Los logs deben conservar `traceId`, `spanId` y las claves del MDC (`messageId`, `orderId`, `correlationId`).
- Reutilizar lo que ya existe (collector, OTLP) antes de sumar mecanismos nuevos.
- La salida por consola y los tests existentes no cambian.
- Versiones verificadas contra la fuente oficial, no escritas de memoria.

## Opciones consideradas

1. **OTLP de cada servicio al collector y de ahí a Seq (elegida).** Reutiliza el pipeline de trazas. Los servicios solo conocen al collector; si Seq cae, el collector reintenta y descarta tras su límite sin bloquear a nadie.
2. **OTLP directo de cada servicio a Seq.** Una pieza menos, pero cada servicio queda acoplado a Seq y la configuración del destino se repite en tres lugares.
3. **Appender específico de Seq (CLEF/GELF por HTTP).** Agrega una librería de terceros distinta de la que ya se usa para trazas y no lleva el `traceId` con el mismo formato.
4. **Driver de logging de Docker (GELF) hacia Seq.** No requiere cambios en el código, pero pierde los atributos estructurados del MDC (llegan dentro de un texto) y obliga a un contenedor intermedio de ingesta GELF.

## Decisión

Opción 1.

- Dependencia `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` en los tres servicios, con la versión de instrumentación que acompaña al SDK de OpenTelemetry que gestiona cada Spring Boot: `2.28.1-alpha` para el SDK 1.62.0 de Boot 4.1.1 (servicios, propiedad en el `pom.xml` raíz) y `2.21.0-alpha` para el SDK 1.55.0 de Boot 4.0.8 (gateway, propiedad en su `pom.xml`). Boot no gestiona este artefacto, así que cada versión se verificó contra el BOM de instrumentación en Maven Central. El artefacto es alpha: la API del appender puede cambiar entre versiones menores.
- Un `logback-spring.xml` por servicio que conserva el appender de consola de Spring Boot (formato estructurado `logstash`) y agrega el appender de OpenTelemetry con `captureMdcAttributes=*` y `captureKeyValuePairAttributes=true`.
- Un componente `InstalarAppenderOpenTelemetry` en `configuracion` instala el bean `OpenTelemetry` en el appender.
- La exportación se activa solo con `management.opentelemetry.logging.export.otlp.endpoint`; en compose vale `http://otel-collector:4318/v1/logs`.
- El collector agrega un pipeline `logs` (`otlp` → `batch` → `otlphttp/seq` con endpoint `http://seq/ingest/otlp`; Seq recibe OTLP/HTTP protobuf en `/ingest/otlp/v1/logs`).
- Servicio `seq` en compose: imagen `datalust/seq:2026.1.17182`, `ACCEPT_EULA=Y`, `SEQ_FIRSTRUN_NOAUTHENTICATION=true`, puerto `5342`, volumen `seq-data`. Los servicios de aplicación no declaran `depends_on` hacia Seq.

## Consecuencias

**Positivas**
- Un solo lugar para buscar y filtrar logs de los tres servicios, por ejemplo por `correlationId`, `orderId` o `traceId`.
- Sin cambios en el código de negocio ni en el contrato de mensajes.

**Negativas y riesgos**
- El appender es alpha. Mitigación: la versión la fija el BOM de Spring Boot y la prueba de integración (`007-AC-01`) falla si cambia el comportamiento.
- Se suma un contenedor (unos 200 MB de memoria) y sube la memoria total del stack. Mitigación: Seq es una sola instancia sin dependencias.
- Seq sin autenticación y con licencia gratuita de un solo usuario: válido para desarrollo local; en producción habría que activar autenticación, API keys y retención.
- Los logs emitidos antes de instalar el appender se acumulan en su buffer interno; si el arranque falla antes de instalarlo, esos logs solo quedan en la consola.
- Si el collector o Seq caen, los logs no entregados dentro del límite de reintentos del collector se pierden de Seq (siguen en la consola).

## Cómo se verifica

- `007-AC-01`: IT en cada servicio con un `LogRecordExporter` en memoria (atributos del MDC y `traceId`).
- `007-AC-02`: la suite existente corre sin endpoint configurado.
- `007-AC-03` y `007-AC-04`: verificación en el stack de compose contra la API de Seq (`/api/events`), descrita en el README.

## Referencias

- [Spec 007](../../specs/007-logs-en-seq/spec.md) · [Spec 006](../../specs/006-observabilidad/spec.md)
- [Logging from OpenTelemetry (Seq)](https://docs.datalust.co/docs/ingestion-with-opentelemetry)
- [OpenTelemetry with Spring Boot](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/)
