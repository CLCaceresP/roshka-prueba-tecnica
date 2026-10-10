# Front local

Requiere el stack encendido, y Node.js 22 o superior. El front no necesita instalar paquetes.

Desde la raíz del repositorio:

```bash
scripts/front.sh      # Git Bash, Linux o macOS
```

```powershell
scripts\front.ps1     # PowerShell
```

Abrir http://localhost:5173. También se puede ejecutar `node front/servidor.mjs`.
Para cambiar el puerto o el gateway: `scripts/front.sh --puerto 5174 --gateway http://localhost:8080` o, en PowerShell, `scripts\front.ps1 -Puerto 5174 -Gateway http://localhost:8080`.
Ctrl+C cierra solamente el front. No detiene los contenedores.

La solicitud, sus cabeceras y la respuesta del API se muestran en el panel derecho, que permanece visible al desplazarte. En pantallas pequeñas se muestran debajo de los formularios.

## Productos y stock

El catálogo viene de `GET /products`, con páginas de 20 productos y totales. **Anterior**, **Siguiente** y **Actualizar** consultan el backend. Se puede seleccionar un producto o pegar su UUID y consultar sus datos o stock.

No se guarda una lista de productos en el navegador. Un 404 limpia el producto y su stock visible; un fallo del catálogo vacía el selector y permite reintentar.

Al crear un producto, el backend normaliza el nombre y rechaza duplicados con `409 PRODUCT_ALREADY_EXISTS`.

## Pedidos

1. Crear un producto con un nombre nuevo y 5 unidades.
2. Crear un pedido de 2 unidades: pasa de `PENDING` a `CONFIRMED`; quedan 3 disponibles y 2 reservadas.
3. Pulsar **Repetir misma solicitud**: conserva cuerpo y `Idempotency-Key`; devuelve el mismo pedido sin reservar más stock. La respuesta almacenada puede mostrar `PENDING`; la consulta automática recupera el estado actual.
4. Cancelar el pedido: pasa a `CANCELLED` y el stock vuelve a 5 disponibles y 0 reservadas después de procesar la liberación.
5. Crear otro pedido con una cantidad mayor al stock: termina en `REJECTED` sin descontar unidades.
6. Pulsar **Copiar filtro para Seq** y pegarlo en http://localhost:5342. Para un pedido consultado por ID se copia un filtro por `orderId`.

El listado consulta `GET /orders`, con fechas y páginas de 20 pedidos. Sin fechas muestra hoy; con una sola fecha muestra ese día. El rango máximo es de 31 días. El formulario transforma las fechas al formato `MM/dd/yyyy` que requiere el backend. **Ver pedido** carga el detalle y permite cancelarlo.

La cancelación envía `DELETE /orders/{id}`, con `Idempotency-Key`.

## Actualizar stock

El formulario envía `PUT /products/{id}/stock` con `{"available": n, "reason": "motivo opcional"}`.

Seleccionar un producto e ingresar las unidades disponibles que deben quedar, entre 0 y 10.000.000. Si hay 5 y se quieren dejar 8, ingresar 8. Las unidades reservadas no cambian y el total pasa a ser disponibles más reservadas. El motivo es opcional. Si el valor ya coincide, no se registra movimiento.

**Repetir misma solicitud** conserva cuerpo, producto y `Idempotency-Key`. No crea otro movimiento. Después de guardar o repetir, se consultan el stock actual, el catálogo y el historial; así no se presenta como actual el stock de una respuesta idempotente antigua.

**Consultar movimientos** muestra el historial paginado, del más reciente al más antiguo, con la diferencia aplicada, el motivo y el stock posterior. El stock inicial no aparece como ajuste. Si disponibles más reservadas supera el máximo, se muestra `409 STOCK_LIMIT_EXCEEDED` con el detalle del API.

## Implementación y comprobaciones

Las solicitudes modifican los datos reales del stack local. HTML, CSS y JavaScript sin dependencias. `servidor.mjs` escucha en loopback y reenvía las rutas admitidas al gateway, incluidos los parámetros de fecha y paginación. Conserva estados HTTP y cabeceras de correlación e idempotencia. Las respuestas y las consultas del navegador usan `no-store`.

El enlace de Swagger apunta a http://localhost:8080/docs, donde se pueden elegir pedidos e inventario.

```powershell
node --test front/app.test.mjs
node --check front/app.js
node --check front/servidor.mjs
```

Las pruebas del cliente usan un DOM simulado y verifican catálogo, paginación, fallos de conexión, descarte por 404, bloqueo de pedidos con productos inexistentes, fechas, detalle de pedidos, ajustes e idempotencia.
