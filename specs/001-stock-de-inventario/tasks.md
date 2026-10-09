# 001 · Tareas

- [x] T-01 · `Producto` rechaza estados que violan RN-01/RN-02 · cubre 001-AC-07 · nivel: unit · módulo: ms-inventario
- [x] T-02 · Migración V1 con `CHECK` de no negativo y conservación · cubre 001-AC-07 · nivel: it · módulo: ms-inventario
- [x] T-03 · `RegistrarProducto` crea con `total = available = initialQuantity` · cubre 001-AC-01 · nivel: unit · módulo: ms-inventario
- [x] T-04 · `POST /products` → 201 + Location; validación → 400 · cubre 001-AC-01, 001-AC-11 · nivel: it · módulo: ms-inventario
- [x] T-05 · `GET /products/{id}/stock` → 200 / 404 · cubre 001-AC-02, 001-AC-03 · nivel: it · módulo: ms-inventario
- [x] T-06 · `ReservarStock` reserva todas las líneas · cubre 001-AC-04 · nivel: unit · módulo: ms-inventario
- [x] T-07 · `ReservarStock` todo o nada ante stock insuficiente · cubre 001-AC-05 · nivel: unit + it · módulo: ms-inventario
- [x] T-08 · `ReservarStock` rechaza un producto desconocido · cubre 001-AC-06 · nivel: unit + it · módulo: ms-inventario
- [x] T-09 · `JdbcLibroStock` usa `UPDATE` condicional en orden ascendente · cubre 001-AC-04, 001-AC-05 · nivel: it · módulo: ms-inventario
- [x] T-10 · `LiberarStock` devuelve exactamente lo reservado · cubre 001-AC-08 · nivel: unit + it · módulo: ms-inventario
- [x] T-11 · 100 reservas concurrentes por 10 unidades · cubre 001-AC-09 · nivel: concurrency · módulo: ms-inventario
- [x] T-12 · Líneas cruzadas sin deadlock · cubre 001-AC-10 · nivel: concurrency · módulo: ms-inventario
