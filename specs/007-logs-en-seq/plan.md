# 007 · Plan

## Componentes

| Pieza | Dónde | Qué |
|---|---|---|
| Dependencia `opentelemetry-logback-appender-1.0` | `ms-pedidos`, `ms-inventario`, `gateway-pedidos-inventario` | Appender de Logback que convierte cada log en un registro OTel. La versión la gestiona el BOM de Spring Boot. |
| `logback-spring.xml` | `src/main/resources` de cada módulo | Mantiene el appender de consola de Spring Boot (con `logging.structured.format.console`) y agrega el appender de OpenTelemetry con `captureMdcAttributes=*` y atributos clave/valor. |
| `InstalarAppenderOpenTelemetry` | `configuracion` de cada módulo | Instala en el appender el bean `OpenTelemetry` que autoconfigura Spring Boot, para que los logs salgan por el mismo exportador. |
| `management.opentelemetry.logging.export.otlp.endpoint` | `docker-compose.yml` (variable de entorno) | `http://otel-collector:4318/v1/logs`. Sin variable no hay exportación. |
| Pipeline `logs` | `infra/otel-collector/config.yaml` | Receptor `otlp` → `batch` → exportador `otlphttp/seq` hacia `http://seq/ingest/otlp`. |
| Servicio `seq` | `docker-compose.yml` | `datalust/seq` con versión fijada, `ACCEPT_EULA=Y`, sin autenticación, puerto `5342`, volumen de datos. Los servicios de aplicación no dependen de él. |

## Modelo de datos y mensajes

Ninguno. No hay migraciones ni cambios en el contrato de mensajes.

## ADRs

- [0016](../../docs/adr/0016-logs-en-seq-via-otlp.md) · vía de ingesta y destino de los logs.

## Estrategia de tests

| Criterio | Nivel | Cómo |
|---|---|---|
| 007-AC-01 | it (`ms-pedidos`, `ms-inventario`) | Un `LogRecordExporter` de prueba en memoria sustituye al OTLP; se maneja un mensaje real por Kafka y se espera el registro con sus atributos y su `traceId`. |
| 007-AC-05 | it (gateway) | El `LogRecordExporter` en memoria recibe el log de arranque (se acumula en el buffer del appender hasta instalarse) con `service.name` del gateway. |
| 007-AC-02 | it | Toda la suite existente corre sin endpoint configurado. |
| 007-AC-03, 007-AC-04 | compose | Verificación manual con `scripts/up.sh` y la API de Seq (`/api/events`), documentada en el README. |
