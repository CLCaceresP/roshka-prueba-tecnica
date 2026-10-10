# 014 · Plan

| Pieza | Dónde | Qué |
|---|---|---|
| `AjusteStock` | `dominio` de `ms-inventario` | Valida `available` (0 a 10.000.000) y normaliza el motivo. |
| `TipoAjuste`, `MovimientoStock` | `dominio` | `ADD` si el stock subió y `REMOVE` si bajó; el movimiento es un record inmutable. |
| `Producto.TOTAL_MAXIMO` | `dominio` | Tope del `total` (10.000.000). |
| `LimiteStockExcedido` | `dominio` | Error de dominio traducido a 409. |
| `AjustarStock`, `ListarMovimientos` | puertos de entrada | Casos de uso. |
| `AjusteStockHandler`, `ConsultaMovimientos` | `aplicacion` | Con `Pasos`; el cambio va dentro de la transacción de idempotencia. |
| `LibroStock.fijarDisponible` | puerto de salida y `JdbcLibroStock` | Un `UPDATE` con `FOR UPDATE` que devuelve el valor anterior y el producto resultante. |
| `MovimientosStock` | puerto de salida y adaptador JDBC | `insertar` y `listar(productId, pagina)`. |
| `V5__stock_movements.sql` | `ms-inventario` | Tabla `stock_movements` (ya existente) con `CHECK` de `type` y `quantity`. |
| `ProductoController` | `adaptadores/entrada/rest` | `PUT .../stock` y `GET .../stock/movements`. |
| `ErroresApi` | `adaptadores/entrada/rest` | `STOCK_LIMIT_EXCEEDED`. |

## Estrategia de tests

| Criterio | Nivel |
|---|---|
| 014-AC-05 (rangos y motivo) | unit (`AjusteStockTest`) |
| 014-AC-01, 014-AC-02, 014-AC-03, 014-AC-04, 014-AC-06 | unit con fake (`AjusteStockHandlerTest`) e it (`InventarioPersistenciaIT`) |
| 014-AC-05, 014-AC-07, 014-AC-10 | it (`AjusteStockApiIT`) |
| 014-AC-08, 014-AC-09 | concurrencia (`InventarioConcurrenciaIT`) |
| 014-AC-11 | it (`LogsPasosIT`) |
