# 012 · Listado de productos y consulta de un producto

Estado: Aprobada
Requisitos: EXT-01, REQ-23, REQ-19
Decisiones: [ADR 0021](../../docs/adr/0021-paginacion-por-pagina-y-fechas-mm-dd-yyyy.md)

## Contexto

Hoy solo se puede consultar el stock de un producto cuyo id ya se conoce. Falta poder ver el catálogo y un producto completo (con su nombre). No hay estado habilitado/deshabilitado: un producto sin unidades se reconoce por `available = 0`.

## Reglas de negocio

- **RN-01** `GET /products` devuelve una página de productos: `items` (cada uno con `productId`, `name`, `total`, `available`, `reserved`), `page`, `size`, `totalElements` y `totalPages`.
- **RN-02** El orden es estable: por nombre normalizado sin distinguir mayúsculas y, a igualdad, por `productId`. Lo respalda el índice único `products_name_unique`, así que las páginas no se solapan.
- **RN-03** `page` empieza en 0 y vale 0 por defecto; `size` vale 20 por defecto y debe estar entre 1 y 100. Los límites viven en `application.yml`. Un valor fuera de rango o que no es un número responde `400 VALIDATION_FAILED` con el detalle del parámetro.
- **RN-04** Una página fuera de rango no es un error: devuelve `items` vacío y los totales correctos.
- **RN-05** `GET /products/{productId}` devuelve el producto con la misma forma que `POST /products`. Un producto desconocido responde `404 PRODUCT_NOT_FOUND`.
- **RN-06** Son lecturas: no exigen `Idempotency-Key`.
- **RN-07** Los logs dicen qué se devolvió (spec 010): `Listado de productos: página 0 de 3 (20 de 45 productos)`.

## Criterios de aceptación

### 012-AC-01 · El listado devuelve una página con sus totales (EXT-01)
- Given 45 productos registrados
- When llega `GET /products?page=1&size=20`
- Then responde `200` con 20 `items`, `page = 1`, `size = 20`, `totalElements = 45` y `totalPages = 3`, ordenados por nombre

### 012-AC-02 · Los valores por defecto (EXT-01)
- Given productos registrados
- When llega `GET /products` sin parámetros
- Then devuelve la página 0 con tamaño 20

### 012-AC-03 · Los parámetros inválidos son 400 (REQ-19)
- Given `size=0`, `size=101`, `page=-1` o `size=abc`
- When llega `GET /products`
- Then responde `400 VALIDATION_FAILED` y el detalle nombra el parámetro y el rango permitido

### 012-AC-04 · Una página vacía no es un error (EXT-01)
- Given 3 productos
- When llega `GET /products?page=5`
- Then responde `200` con `items` vacío y `totalElements = 3`

### 012-AC-05 · El orden no repite ni omite productos (EXT-01)
- Given productos con nombres que solo difieren en mayúsculas
- When se recorren todas las páginas
- Then cada producto aparece exactamente una vez

### 012-AC-06 · Consultar un producto (EXT-01, REQ-23)
- Given un producto registrado
- When llega `GET /products/{productId}`
- Then responde `200` con `productId`, `name`, `total`, `available` y `reserved`
- And un id desconocido responde `404 PRODUCT_NOT_FOUND`

### 012-AC-07 · Las consultas dicen qué devolvieron (BON-03)
- Given un listado o una consulta
- Then el log dice cuántos productos devolvió, de cuántos y de qué página, o cuál producto devolvió y con cuánto stock

## Casos borde
- Con la tabla vacía: `items` vacío, `totalElements = 0`, `totalPages = 0`.
- Un listado concurrente con altas de productos puede variar entre páginas; es el comportamiento esperable de la paginación por página.

## Fuera de alcance
- Filtros por nombre o por stock, y ordenamientos alternativos.
- Estado habilitado/deshabilitado de producto (no existe).

## Preguntas abiertas
Ninguna.
