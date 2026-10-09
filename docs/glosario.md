# Glosario del dominio

Decisión: [ADR 0015](adr/0015-codigo-en-espanol-y-contratos-externos-en-ingles.md). Fija el vocabulario para nombrar con coherencia el código, las specs y los ADR.

## Módulos y servicios

| Módulo | Responsabilidad |
|---|---|
| `ms-pedidos` | Agregado `Pedido` y orquestador de la saga. |
| `ms-inventario` | Agregados `Producto` y `Reserva`, y el stock. |
| `gateway-pedidos-inventario` | Punto de entrada: ruteo, correlación, rate limiting. |
| `contrato-eventos` | Comandos, eventos y sobre de los mensajes. |
| `pruebas-sistema` | Pruebas de punta a punta contra el stack de `docker-compose`. |
| `pedidos-inventario` | Proyecto Maven raíz (agregador). |

## Paquetes

| Paquete | Contenido |
|---|---|
| `pedidos`, `inventario`, `contrato`, `gateway`, `sistema` | Raíz de cada módulo, bajo `com.roshka.challenge`. |
| `dominio` | Modelo puro: invariantes y máquina de estados. |
| `aplicacion`, `aplicacion.puertos.entrada`, `aplicacion.puertos.salida` | Casos de uso y puertos hexagonales. |
| `adaptadores.entrada`, `adaptadores.salida` | Controllers, listeners, JPA, JDBC, outbox, Redisson. |
| `mensajeria`, `persistencia`, `bloqueo`, `metricas`, `planificacion`, `idempotencia` | Subpaquetes de los adaptadores y de los casos de uso. |
| `configuracion` | Wiring de Spring. |

## Dominio

| Término | Significado |
|---|---|
| `Pedido`, `LineaPedido` | Agregado de `ms-pedidos`. |
| `EstadoPedido` | Constantes `PENDING`, `CONFIRMED`, `REJECTED`, `CANCELLED`: las fija el enunciado. |
| `MotivoRechazoPedido` | Constantes en inglés: se persisten y salen por HTTP. |
| `ColocarPedido`, `ColocacionPedido` | Puerto de entrada y caso de uso de creación. |
| `CancelarPedido`, `CancelacionPedido` | Puerto de entrada y caso de uso de cancelación. |
| `ObtenerPedido`, `ConsultaPedido` | Puerto de entrada y caso de uso de consulta. |
| `Producto` | Agregado de `ms-inventario`. |
| `Reserva`, `LineaReserva`, `EstadoReserva` | Constantes `RESERVED`, `RELEASED`, `REJECTED` en inglés (se persisten). |
| `MotivoRechazo` | Dominio de inventario; constantes en inglés. |
| `LibroStock` | Operaciones de stock sobre la base. |
| `ReservarStock`, `LiberarStock` | Comandos. |
| `StockReservado`, `StockRechazado`, `StockLiberado` | Eventos. |
| `MotivoRechazoStock`, `MotivoLiberacion`, `ResultadoLiberacion` | Enums del contrato, en español. |
| `SobreMensaje`, `TiposMensaje`, `Topicos` | Sobre común, tipos y nombres de tópicos. |
| `SagaReserva`, `PasosSaga`, `TimeoutSagaHandler` | Orquestación de la saga y su timeout. |
| `BloqueoDistribuido`, `BloqueosPedido` | Puerto y uso de los locks distribuidos. |
| `AlmacenClavesIdempotencia`, `ProcesadorSolicitudIdempotente` | Idempotencia HTTP. |
| `MensajesProcesados` | Tabla `processed_messages`. |

## Campos del sobre y de las cargas de los eventos

| Campo | Significado |
|---|---|
| `idMensaje` | Identificador único del mensaje (clave de idempotencia del consumidor). |
| `tipo` | Nombre del comando o evento. |
| `versionEsquema` | Versión del contrato. |
| `ocurridoEn` | Instante en que ocurrió. |
| `idCorrelacion` | Hilo de trazabilidad de la solicitud. |
| `idCausa` | Mensaje que originó este. |
| `idAgregado` | Identificador del pedido; es la key de partición. |
| `carga` | Contenido específico del mensaje. |
| `idPedido`, `idProducto`, `cantidad`, `lineas` | Datos del pedido y de sus ítems. |
| `motivo`, `desenlace`, `idsProducto` | Razón del rechazo o la liberación, resultado y productos afectados. |

Tópicos: `inventario.comandos` e `inventario.eventos`, con sus `.DLT`.

## Lo que se queda en inglés

| Qué | Por qué |
|---|---|
| Paths, headers y campos JSON del API HTTP (`/orders`, `Idempotency-Key`, `orderId`, `lines`, `quantity`) | Contrato del enunciado (REQ-19 a REQ-24). Los DTO usan `@JsonProperty`. |
| Valores de estados y enums persistidos o expuestos por HTTP | Enunciado y esquema (`PENDING`, `RESERVED`, `INSUFFICIENT_STOCK`, `TIMEOUT`…). |
| Campo `code` de los errores (`ORDER_NOT_FOUND`, `VALIDATION_FAILED`…) | Parte del API. |
| Tablas, columnas y bases de datos (`orders`, `order_lines`, `reservations`, `orders_db`) | Migraciones de Flyway inmutables (invariante 14). Las entidades JPA llevan `@Column(name = ...)`. |
| Nombres y etiquetas de métricas, atributos de los logs exportados a Seq, claves del MDC (`messageId`, `orderId`, `correlationId`), nombres de spans | Contrato de observabilidad. |
| Patrones y frameworks: `Outbox`, `Saga`, `DLT`, `Controller`, `Listener`, `Handler`, `Repository`, `Properties`, `Configuration`, `Filter`, `Entity`, `Request`, `Response`, `Job`, `Kafka`, `Jdbc`, `Jpa`, `Redis` | Vocabulario común de la industria y de Spring. |

## Convenciones de nombre

- Métodos en infinitivo (`reservarStock`, `marcarProcesado`), booleanos con `esta…`, `tiene…` o `es…`.
- Colecciones en plural (`lineas`, `reservas`); identificadores con prefijo `id` (`idPedido`, `idProducto`).
- Constantes en `MAYUSCULAS_CON_GUION_BAJO` sin tildes (`CANTIDAD_MAXIMA`).
- Tests `debe_<resultado>_cuando_<condicion>`.
