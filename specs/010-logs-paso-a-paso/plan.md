# 010 · Plan

## Componentes

| Pieza | Dónde | Qué |
|---|---|---|
| `Pasos` | `aplicacion` de `ms-pedidos` y de `ms-inventario` | Clase sin dependencias de framework (solo SLF4J). `Pasos.proceso(origen, "Colocar pedido")` crea un proceso; `.medir(paso, accion)` ejecuta un paso, lo cronometra y lo registra; `.con(clave, valor)` agrega atributos (`orderId`, `productId`); `.ejecutar(cuerpo)` registra el total del proceso. Omite las claves que el MDC ya aporta. |
| `RegistroSolicitudesFilter` | `adaptadores/entrada/rest` de ambos servicios | Agrega la línea de entrada y vuelve más legible la de salida. |
| `RegistroSolicitudes` | `gateway-pedidos-inventario` | Igual: entrada y salida, con `routeId`. |
| Casos de uso | `aplicacion` | `ColocacionPedido`, `CancelacionPedido`, `PasosSaga`, `ProcesadorSolicitudIdempotente`, `RegistroProducto`, `ConsultaStockProducto`, `ReservarStockHandler`, `LiberarStockHandler`. |
| `RedissonBloqueoDistribuido` | `adaptadores/salida/bloqueo` | Registra la adquisición y la liberación con sus tiempos. |
| Listeners (`ComandosInventarioListener`, `RespuestaInventarioListener`) | `adaptadores/entrada/mensajeria` | Registran `Evento recibido`, `Procesando evento` y `Evento finalizado`/`Evento falló`, y dejan en el MDC `eventType`, `eventTopic`, `eventPartition`, `eventOffset` y `causationId` mientras dura el procesamiento. |
| `OutboxPublisher` | `adaptadores/salida/mensajeria` | `Evento publicado` con el tiempo de la confirmación del broker. |
| `ManejoErroresKafka` | `configuracion` | El log de la DLT trae tópico, partición, offset y causa. |
| `SagaReserva` | `aplicacion/saga` | `sagaDurationMs` al llegar a un estado final y mensajes con el pedido, lo que pidió y el motivo. |
| `LibroStock.Rechazado` | puerto de salida de `ms-inventario` | Pasa a incluir los faltantes (`idProducto`, solicitado, disponible) que ya calcula `JdbcLibroStock`, para que la reserva rechazada se explique con cifras. Sin cambios en el contrato de mensajes. |
| `LectorMensajes` | `adaptadores/entrada/mensajeria` | La vista previa del evento suma un resumen legible del contenido (líneas, motivo, desenlace). |

## Modelo de datos y mensajes

Ninguno.

## ADRs

- [0019](../../docs/adr/0019-logs-por-pasos-con-tiempos.md) · cómo se cronometran y registran los pasos.

## Estrategia de tests

| Criterio | Nivel | Cómo |
|---|---|---|
| `Pasos` (010-AC-04, parte) | unitario | Appender de Logback en memoria; sin Spring. |
| 010-AC-01 | it (ambos servicios y gateway) | Exportador de logs en memoria; comparar el orden de los registros por tiempo. |
| 010-AC-02, 010-AC-04 | it (`ms-pedidos`) | `POST /orders` con `MockMvcTester`, dos veces con la misma clave. |
| 010-AC-03 | it (`ms-inventario`) | `POST /products` y un `ReservarStock` por Kafka. |
| 010-AC-05, 010-AC-06 | it | Sobre los flujos Kafka existentes y el outbox. |
| 010-AC-07 | it (ambos servicios) | Respuestas HTTP con cuerpos que incluyen ids propios. |
| 010-AC-08 | compose | Verificación manual contra la API de Seq. |
