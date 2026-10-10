# 013 · Pedidos por rango de fechas con paginación

Estado: Aprobada
Requisitos: EXT-02, REQ-20, REQ-19
Decisiones: [ADR 0021](../../docs/adr/0021-paginacion-por-pagina-y-fechas-mm-dd-yyyy.md)

## Contexto

Hoy un pedido solo se consulta por id. Falta listar los pedidos creados en un rango de fechas, con paginación.

## Reglas de negocio

- **RN-01** `GET /orders` devuelve los pedidos cuya fecha de creación (`createdAt`) cae dentro del rango, en una página: `items` (la misma forma que `GET /orders/{orderId}`, con sus líneas), `page`, `size`, `totalElements` y `totalPages`.
- **RN-02** `from` y `to` son fechas con formato `MM/dd/yyyy` (por ejemplo `10/01/2026`). Ambas son inclusivas y abarcan días completos: el rango es `[from 00:00, to + 1 día 00:00)` en la zona horaria configurada (`consulta.zona-horaria`, por defecto `America/Asuncion`).
- **RN-03** Valores por defecto: sin `from` ni `to`, se usa el día de hoy; con solo `from`, `to = from`; con solo `to`, `from = to`.
- **RN-04** El rango debe cumplir `from <= to` y abarcar como máximo 31 días (`consulta.rango-maximo-dias`, configurable). Si no, responde `400 VALIDATION_FAILED` con el detalle. Una fecha mal formada o inexistente (`13/45/2026`) también es `400`.
- **RN-05** Paginación como en la [spec 012](../012-listado-de-productos/spec.md): `page` desde 0 (por defecto 0), `size` entre 1 y 100 (por defecto 20).
- **RN-06** El orden es del más reciente al más antiguo, con desempate por `orderId`, para que las páginas no se solapen.
- **RN-07** Es una lectura: no exige `Idempotency-Key`.
- **RN-08** Los logs dicen el rango efectivo y qué se devolvió: `Listado de pedidos del 10/01/2026 al 10/10/2026: página 0 de 2 (20 de 31 pedidos)`.

## Criterios de aceptación

### 013-AC-01 · Se devuelven los pedidos del rango (EXT-02)
- Given pedidos creados el 10/08, el 10/09 y el 10/10 de 2026
- When llega `GET /orders?from=10/09/2026&to=10/10/2026`
- Then devuelve solo los de esos dos días, completos (de las 00:00 del 10/09 al fin del 10/10)

### 013-AC-02 · Los valores por defecto son el día de hoy (EXT-02)
- Given pedidos de hoy y de ayer
- When llega `GET /orders` sin parámetros
- Then devuelve solo los de hoy
- And con solo `from` o solo `to` el rango es ese día

### 013-AC-03 · La paginación incluye el total (EXT-02)
- Given 45 pedidos en el rango
- When llega `GET /orders?from=10/10/2026&to=10/10/2026&page=2&size=20`
- Then devuelve 5 `items` y `totalElements = 45`, `totalPages = 3`

### 013-AC-04 · El orden es estable (EXT-02)
- Given pedidos con el mismo `createdAt`
- When se recorren todas las páginas
- Then cada pedido aparece una sola vez, del más reciente al más antiguo

### 013-AC-05 · Los parámetros inválidos son 400 (REQ-19)
- Given `from=2026-10-01`, `from=13/45/2026`, `from` posterior a `to`, un rango de 32 días, `size=101` o `page=-1`
- When llega `GET /orders`
- Then responde `400 VALIDATION_FAILED` y el detalle dice cuál parámetro falla y por qué

### 013-AC-06 · Cada pedido trae sus líneas (EXT-02, REQ-20)
- Given un pedido con dos líneas en el rango
- When se lista
- Then el item incluye `orderId`, `status`, `rejectionReason`, `lines`, `createdAt` y `updatedAt`

### 013-AC-07 · Un rango sin pedidos no es un error (EXT-02)
- Given ningún pedido en el rango
- Then responde `200` con `items` vacío y `totalElements = 0`

### 013-AC-08 · La consulta usa un índice (REQ-16)
- Given la tabla `orders`
- Then existe un índice sobre `(created_at desc, id desc)` creado por una migración nueva

### 013-AC-09 · Los logs dicen el rango y lo devuelto (BON-03)
- Given un listado
- Then el log dice el rango efectivo, la página y cuántos pedidos devolvió de cuántos

## Casos borde
- El día de hoy se calcula con el reloj inyectado y la zona configurada; un test fija el reloj.
- Los cambios de horario de la zona no alteran los límites porque se calculan con `ZonedDateTime`.

## Fuera de alcance
- Filtrar por estado del pedido y paginación por cursor.

## Preguntas abiertas
Ninguna.
