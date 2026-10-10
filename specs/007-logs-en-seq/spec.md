# 007 · Logs centralizados en Seq

Estado: Aprobada
Requisitos: BON-03, REQ-18

## Contexto

Hoy los logs de `ms-pedidos`, `ms-inventario` y `gateway-pedidos-inventario` salen como JSON por consola y solo se leen con `docker compose logs`, servicio por servicio. Para seguir un pedido por los tres servicios hay que juntar a mano varias salidas. Se agrega **Seq** como destino central de logs, junto a Jaeger (trazas) y Prometheus (métricas) de la [spec 006](../006-observabilidad/spec.md).

Los logs viajan por OTLP al OTel Collector, que ya recibe las trazas, y el collector los reenvía a Seq ([ADR 0016](../../docs/adr/0016-logs-en-seq-via-otlp.md)). La consola se mantiene.

## Reglas de negocio

- **RN-01** Todo log de los tres servicios llega a Seq con nivel, mensaje, logger y el `service.name` del servicio que lo emitió.
- **RN-02** Los logs conservan el contexto de la spec 006: `traceId`, `spanId` y las claves del MDC `messageId`, `orderId` y `correlationId` viajan como atributos y se pueden filtrar en Seq.
- **RN-03** La exportación solo se activa si hay un endpoint configurado (`management.opentelemetry.logging.export.otlp.endpoint`). Sin endpoint, el servicio arranca igual y solo escribe por consola.
- **RN-04** Seq es un destino adicional: la salida JSON por consola no cambia y una caída de Seq o del collector no afecta a los servicios ni a su readiness.
- **RN-05** Seq corre en el stack de `docker-compose`, con la imagen fijada a una versión, la interfaz en `http://localhost:5342` y sin autenticación (solo desarrollo local).

## Criterios de aceptación

### 007-AC-01 · Un log llega al exportador OTLP con su contexto (BON-03)
- Given un servicio con un exportador de logs OTLP configurado
- When maneja un mensaje y registra el log de la operación
- Then el exportador recibe un registro con el mensaje, el nivel, `messageId`, `orderId` y `correlationId` como atributos y el `traceId` de la operación

### 007-AC-05 · Los logs del gateway y los de arranque se exportan (BON-03)
- Given el gateway con un exportador de logs OTLP configurado
- When arranca
- Then el exportador recibe el log de arranque del servidor con nivel INFO y el recurso `service.name = gateway-pedidos-inventario`

### 007-AC-02 · Sin endpoint no se exporta nada (BON-03)
- Given un servicio sin `management.opentelemetry.logging.export.otlp.endpoint`
- When arranca y registra logs
- Then la salida por consola es la de siempre y todos los tests existentes siguen en verde

### 007-AC-03 · Los tres servicios aparecen en Seq (BON-03, REQ-18)
- Given el stack de compose levantado
- When se crea y confirma un pedido a través del gateway
- Then Seq tiene eventos de `gateway-pedidos-inventario`, `ms-pedidos` y `ms-inventario`, y filtrando por el `correlationId` de la solicitud aparecen los de `ms-pedidos` y `ms-inventario`

### 007-AC-04 · Una caída de Seq no afecta a los servicios (BON-03)
- Given el stack levantado
- When se detiene el contenedor de Seq
- Then los servicios siguen `healthy` y atienden solicitudes

## Casos borde
- Logs emitidos durante el arranque, antes de instalar el appender: se acumulan en el buffer del appender y se exportan al instalarse (007-AC-05).
- El gateway no registra una línea por solicitud, así que no hay logs suyos con `correlationId`; sus logs llevan nivel, mensaje, logger y `service.name`.
- Seq no disponible: el collector reintenta y descarta tras su límite; los servicios no se bloquean.

## Fuera de alcance
- Autenticación, API keys y retención de Seq.
- Dashboards y alertas basados en logs.
- Eliminar la salida por consola.

## Preguntas abiertas
Ninguna.
