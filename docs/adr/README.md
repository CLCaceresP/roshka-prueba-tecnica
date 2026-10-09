# Registro de decisiones de arquitectura

Formato MADR ([0001](0001-registrar-decisiones-de-arquitectura.md)). Se crean con `/adr <título>`.

| # | Decisión | Estado | Requisitos |
|---|---|---|---|
| [0001](0001-registrar-decisiones-de-arquitectura.md) | Registrar decisiones con MADR | Aceptado | BON-02, BON-04 |
| [0002](0002-kafka-como-sistema-de-mensajeria.md) | Kafka, at-least-once, key = idPedido | Aceptado | REQ-17, REQ-18, REQ-26 |
| [0003](0003-outbox-con-polling-y-shedlock.md) | Transactional Outbox con polling y ShedLock | Aceptado | REQ-06, REQ-26, REQ-27 |
| [0004](0004-saga-orquestada-dentro-de-ms-pedidos.md) | Saga orquestada dentro de ms-pedidos | Aceptado | REQ-07, REQ-27 |
| [0005](0005-bloqueo-distribuido-como-defensa-en-profundidad.md) | Redisson como defensa en profundidad | Aceptado | REQ-24, REQ-25 |
| [0006](0006-reintentos-bloqueantes-y-dlt.md) | Reintentos bloqueantes y DLT | Aceptado | REQ-18, REQ-26 |
| [0007](0007-gateway-delgado.md) | Gateway delgado | Aceptado | REQ-24, BON-02 |
| [0008](0008-plataforma-y-versiones.md) | Java 25; Boot 4.1 en servicios, 4.0 + Spring Cloud 2025.1 en gateway | Aceptado | REQ-01, REQ-28 |
| [0009](0009-anti-sobreventa-en-sql.md) | No sobreventa por UPDATE condicional, CHECK y orden ascendente | Aceptado | REQ-11, REQ-13 |
| [0010](0010-consumidor-idempotente-y-tombstone.md) | Consumidor idempotente, reserva como punto de serialización y tombstone | Aceptado | REQ-14, REQ-26, REQ-27 |
| [0011](0011-idempotencia-http.md) | Idempotencia HTTP en una sola transacción, lock como primera barrera | Aceptado | REQ-24, REQ-25 |
| [0012](0012-maquina-de-estados-de-pedido.md) | Máquina de estados de Pedido: patrón State sobre un enum | Aceptado | REQ-07, REQ-08 |
| [0013](0013-timeout-de-saga.md) | Timeout de saga con job y ShedLock, seguro por el tombstone | Aceptado | REQ-07, REQ-27 |
| [0014](0014-renovacion-outbox-y-recuperacion-dlt.md) | Renovación del lock del outbox, DLT confirmada y recuperación manual de liberaciones | Aceptado | REQ-06, REQ-12, REQ-18, REQ-26, REQ-27 |
| [0015](0015-codigo-en-espanol-y-contratos-externos-en-ingles.md) | Código en español; contratos externos y datos persistidos en inglés | Aceptado | REQ-01, REQ-16, REQ-19 a REQ-24 |
| [0016](0016-logs-en-seq-via-otlp.md) | Logs en Seq por OTLP a través del OTel Collector | Aceptado | BON-03, REQ-18 |
| [0017](0017-logs-de-solicitudes-y-enrichers.md) | Logs de solicitudes con filtro propio y enrichers como atributos de recurso | Aceptado | BON-03, REQ-18 |
| [0018](0018-mini-front-local.md) | Mini front local sin dependencias y proxy al gateway | Aceptado | REQ-19 a REQ-24, REQ-28, BON-03 |
| [0019](0019-logs-por-pasos-con-tiempos.md) | Logs por pasos con tiempos mediante un cronometraje explícito | Aceptado | BON-03, REQ-18 |
| [0020](0020-unicidad-del-nombre-y-contrato-http-estricto.md) | Nombre de producto único por índice y contrato HTTP estricto | Aceptado | REQ-16, REQ-19, REQ-22, REQ-24 |
| [0021](0021-paginacion-por-pagina-y-fechas-mm-dd-yyyy.md) | Paginación por página con total y fechas `MM/dd/yyyy` | Aceptado | REQ-19, EXT-01, EXT-02 |
| [0022](0022-ajuste-de-stock-con-put-absoluto.md) | Ajuste de stock con PUT absoluto e historial de movimientos | Aceptado | REQ-10, REQ-13, REQ-24, REQ-31, EXT-03 |
| [0023](0023-openapi-servido-por-el-gateway.md) | OpenAPI servido por el gateway | Aceptado | REQ-24, BON-02, EXT-04 |
