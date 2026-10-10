# 013 · Plan

| Pieza | Dónde | Qué |
|---|---|---|
| `RangoFechas` | `aplicacion` de `ms-pedidos` | Interpreta `MM/dd/yyyy`, aplica los valores por defecto, valida el rango y entrega `Instant desde/hasta`. |
| `SolicitudPagina`, `Pagina<T>`, `ParametroInvalido` | `aplicacion` / `dominio` | Igual que en `ms-inventario` (cada servicio tiene los suyos; no hay lógica compartida). |
| `ListarPedidos` | puerto de entrada | `Pagina<Pedido> listar(RangoFechas, SolicitudPagina)`. |
| `ConsultaPedidos` | `aplicacion` | Caso de uso con `Pasos` y log de lo devuelto. |
| `PedidoRepository.buscarPorCreacion` | puerto de salida y `JpaPedidoRepository` | Primero los ids de la página (`created_at desc, id desc`), luego las líneas en lote (evita el N+1). |
| `V4__orders_created_at_index.sql` | `ms-pedidos` | `create index orders_created_at_id on orders (created_at desc, id desc)`. |
| `PedidoController` | `adaptadores/entrada/rest` | `GET /orders` con `from`, `to`, `page`, `size`. |
| `application.yml` | `ms-pedidos` | `consulta.zona-horaria`, `consulta.rango-maximo-dias`, `paginacion.*`. |

## Estrategia de tests

| Criterio | Nivel |
|---|---|
| 013-AC-02, 013-AC-05 | unit (`RangoFechasTest` con reloj fijo) |
| 013-AC-01, 013-AC-03, 013-AC-04, 013-AC-06, 013-AC-07 | it (`PedidoListadoIT`, Postgres real) |
| 013-AC-08 | it (consulta `pg_indexes`) |
| 013-AC-09 | it (`LogsPasosIT`) |
