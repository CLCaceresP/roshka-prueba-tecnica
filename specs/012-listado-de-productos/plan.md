# 012 · Plan

| Pieza | Dónde | Qué |
|---|---|---|
| `SolicitudPagina`, `Pagina<T>` | `aplicacion` de `ms-inventario` | Valida `page` y `size` contra la configuración y transporta el resultado. |
| `ParametroInvalido` | `dominio` | Error de dominio para parámetros fuera de rango; el controller lo traduce a 400. |
| `ListarProductos` | puerto de entrada | `Pagina<Producto> listar(SolicitudPagina)`. |
| `ConsultaProductos` | `aplicacion` | Caso de uso con `Pasos` y log de lo devuelto; también `obtener(id)`. |
| `ProductoRepository.listar` | puerto de salida y `JdbcProductoRepository` | `order by lower(btrim(name)), id limit/offset` y `count(*)`. |
| `ProductoController` | `adaptadores/entrada/rest` | `GET /products` y `GET /products/{productId}`; `PaginaResponse<T>`. |
| `application.yml` | `ms-inventario` | `paginacion.tamanio-por-defecto: 20`, `paginacion.tamanio-maximo: 100`. |

Sin migración: el índice `products_name_unique` ya ordena por nombre.

## Estrategia de tests

| Criterio | Nivel |
|---|---|
| 012-AC-03, 012-AC-04 | unit (`SolicitudPaginaTest`, `ConsultaProductosTest`) |
| 012-AC-01, 012-AC-02, 012-AC-05 | it (`ProductoListadoIT` con Postgres real) |
| 012-AC-06 | it (`ProductoApiIT`) |
| 012-AC-07 | it (`LogsPasosIT`) |
