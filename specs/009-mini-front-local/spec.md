# 009 · Front local

## Alcance

Cliente local sencillo del gateway; no agrega lógica de negocio ni modifica los servicios. Véase [ADR 0018](../../docs/adr/0018-mini-front-local.md). Incluye los contratos de las specs [011](../011-validaciones-de-producto-y-contrato-http/spec.md), [012](../012-listado-de-productos/spec.md), [013](../013-pedidos-por-fecha/spec.md) y [014](../014-ajuste-de-stock/spec.md).

## Criterios de aceptación

- **REQ-22, REQ-23, REQ-10:** Given el stack encendido, When se crea un producto con 5 unidades, Then se muestran su ID y 5 disponibles, 0 reservadas y 5 totales.
- **REQ-19, REQ-20, REQ-07, REQ-08, REQ-11:** Given ese producto, When se piden 2 unidades, Then se consulta automáticamente hasta `CONFIRMED` y se muestran 3 disponibles y 2 reservadas.
- **REQ-24:** Given una creación de pedido, When se repite la solicitud, Then se reutilizan cuerpo y clave, se recibe el mismo ID y no aumenta la reserva.
- **REQ-21, REQ-12:** Given un pedido confirmado, When se cancela, Then se muestra `CANCELLED` y se actualiza el stock tras la liberación asíncrona.
- **REQ-13:** Given stock insuficiente, When se pide una cantidad superior a las disponibles, Then se muestra `REJECTED` y se conserva el stock.
- **BON-03:** Given un pedido creado desde el front, When se copia el filtro de Seq, Then se obtiene el identificador utilizado en `X-Correlation-Id` y el enlace abre el puerto 5342. Para pedidos consultados por ID se copia el filtro por `orderId`.
- **REQ-28:** Given el repositorio y Node.js, When se ejecuta `scripts/front.ps1`, Then el front se sirve en loopback sin dependencias adicionales y cerrarlo no detiene Docker.
- **REQ-23:** Given un producto seleccionado eliminado del backend, When se consulta su stock o se intenta crear un pedido, Then se limpia su referencia y su stock visible; no se envía la creación del pedido.
- **REQ-22 (spec 011):** Given un nombre ya utilizado, When se crea otro producto con ese nombre, Then se muestra `409 PRODUCT_ALREADY_EXISTS` y su detalle.
- **EXT-01, REQ-23 (spec 012):** Given productos existentes, When se abre la página o se recorre el catálogo, Then se muestran los productos y totales de la página obtenida del backend, sin lista persistida en el navegador. Un fallo de conexión vacía el catálogo visible y permite reintentar.
- **EXT-02, REQ-20 (spec 013):** Given fechas de creación, When se busca y pagina el listado de pedidos, Then se conservan los filtros y se envían fechas `MM/dd/yyyy`; sin fechas se consulta hoy. Un rango de más de 31 días se rechaza antes de enviar la solicitud.
- **EXT-02, REQ-20 (spec 013):** Given un pedido listado, When se pulsa Ver pedido, Then se consulta su detalle y se muestra su estado y su ID.
- **EXT-03, REQ-10, REQ-24 (spec 014):** Given un producto seleccionado, When se fija su stock con `PUT /products/{id}/stock`, Then se envían `available` y `reason` y se muestran el resultado y el stock actual; al repetir se conservan clave y cuerpo y no se genera otro movimiento.
- **EXT-03, REQ-10, REQ-13 (spec 014):** Given un producto con reservas, When se fija `available = 0`, Then se muestran cero disponibles y las reservas conservadas.
- **EXT-03 (spec 014):** Given un stock disponible que ya coincide con el solicitado, When el backend devuelve `movement = null`, Then se informa que no hubo movimiento.
- **EXT-03, REQ-10 (spec 014):** Given un total que superaría el máximo, When el backend rechaza el cambio, Then se muestra `409 STOCK_LIMIT_EXCEEDED` sin alterar el stock visible.
- **EXT-03 (spec 014):** Given ajustes registrados, When se consultan los movimientos, Then se muestra el historial paginado y el stock posterior a cada movimiento.

## Validación

`node --test front/app.test.mjs`: 17 pruebas del cliente con DOM simulado. Cubren catálogo obtenido del backend, paginación, descarte de datos visibles ante fallos, recuperación de conexión, 404, bloqueo de creación con un producto inexistente, conversión y límite de fechas, selección de pedidos, cancelación DELETE idempotente, PUT idempotente, stock cero con reservas conservadas, ausencia de movimiento, valores inválidos, rechazo por tope y nombre duplicado.

El cliente utiliza `DELETE /orders/{id}` para cancelar y `PUT /products/{id}/stock` con `{available, reason}` para fijar disponibles. La respuesta de stock incluye `movement`, que puede ser null. El proxy admite ambos métodos, conserva el cuerpo PUT y descarta las rutas de escritura anteriores. Swagger apunta a `/docs` del gateway. Se comprobaron `/docs` y ambos documentos OpenAPI del gateway: inventario publica GET y PUT de stock, y pedidos publica GET y DELETE por ID.
