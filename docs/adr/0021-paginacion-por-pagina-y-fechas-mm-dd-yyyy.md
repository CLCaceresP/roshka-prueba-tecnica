# 0021 · Paginación por página con total y fechas `MM/dd/yyyy`

- Estado: Aceptado
- Fecha: 2026-10-10
- Requisitos: EXT-01, EXT-02, REQ-19

## Contexto y problema

Los listados de productos, de movimientos y de pedidos necesitan paginación, y el listado de pedidos un rango de fechas. Hay que decidir el estilo de paginación, el formato de las fechas, la zona horaria y los límites.

## Factores de decisión

- Los listados devuelven página con total, con tamaño máximo 100, rango máximo 31 días y fechas `MM/dd/yyyy`, con el día de hoy por defecto.
- Las páginas no deben repetir ni omitir elementos entre solicitudes.
- Ningún número mágico: los límites van en `application.yml`.

## Opciones consideradas para la paginación

1. **Por página (`page`, `size`) con `totalElements` y `totalPages` (elegida).** Simple de usar y de mostrar; permite saltar a una página. El costo es un `count(*)` por solicitud.
2. **Por cursor (keyset).** Escala mejor en tablas enormes y no se desfasa con altas concurrentes, pero no entrega total ni permite saltar, y complica el cliente.
3. **Sin límites, devolver todo.** Descartada: una consulta sin acotar degrada el servicio.

## Opciones consideradas para las fechas

1. **`MM/dd/yyyy`, días inclusivos, en una zona horaria configurada (elegida).** Es el formato que se fijó para el contrato. Con días completos, `to` incluye todo ese día: el rango es `[from 00:00, to + 1 día 00:00)`.
2. **ISO-8601 (`yyyy-MM-dd`).** Menos ambiguo (`04/05/2026` se lee distinto en otros países), pero no es lo pedido. Queda como riesgo documentado.
3. **Instantes con zona.** Exacto, pero incómodo para quien consulta «el día de hoy».

## Decisión

- Paginación por página: `page` desde 0 (defecto 0) y `size` de 1 a 100 (defecto 20). Respuesta: `items`, `page`, `size`, `totalElements`, `totalPages`. Los límites son `paginacion.tamanio-por-defecto` y `paginacion.tamanio-maximo`.
- Orden total determinista en cada listado (nombre normalizado y `id`; `created_at desc` y `id desc`), respaldado por un índice.
- `from` y `to` en `MM/dd/yyyy`, interpretados de forma estricta (`13/45/2026` es inválido). Sin ambos, el día de hoy; con uno solo, ese día. Máximo 31 días (`consulta.rango-maximo-dias`).
- La zona horaria es `consulta.zona-horaria` (por defecto `America/Asuncion`); el día de hoy sale del reloj inyectado.
- Un valor inválido responde `400 VALIDATION_FAILED` y el detalle nombra el parámetro.

## Consecuencias

**Positivas**
- Un cliente puede mostrar «página 3 de 12» y recorrer todo el resultado sin repetidos.
- Los límites se cambian sin tocar el código.

**Negativas y riesgos**
- `count(*)` en cada solicitud: aceptable para este tamaño; con millones de filas habría que pasar a cursor.
- Una alta o baja entre dos solicitudes puede mover un elemento de página; es propio de este estilo.
- `MM/dd/yyyy` es ambiguo para quien espera día/mes: la documentación OpenAPI lo muestra con ejemplo y el `400` explica el formato.
- Si el reloj o la zona del servidor difieren de las del cliente, «hoy» puede diferir; por eso la zona es explícita.

## Cómo se verifica
- Specs 012 y 013: tests de límites, orden estable, rango y valores por defecto con reloj fijo.

## Referencias
- [Spec 012](../../specs/012-listado-de-productos/spec.md) · [Spec 013](../../specs/013-pedidos-por-fecha/spec.md)
