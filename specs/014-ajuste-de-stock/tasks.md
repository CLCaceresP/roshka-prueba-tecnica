# 014 · Tareas

- [x] T-01 · `AjusteStock` valida `available` y motivo · cubre 014-AC-05 · nivel: unit · módulo: ms-inventario
- [x] T-02 · `LibroStock.fijarDisponible` con `UPDATE` bajo `FOR UPDATE` · cubre 014-AC-01, 014-AC-02, 014-AC-03, 014-AC-04, 014-AC-06 · nivel: it · módulo: ms-inventario
- [x] T-03 · `AjusteStockHandler` registra el movimiento en la misma transacción · cubre 014-AC-01, 014-AC-06 · nivel: unit · módulo: ms-inventario
- [x] T-04 · `PUT /products/{productId}/stock` con idempotencia y errores · cubre 014-AC-05, 014-AC-07 · nivel: it · módulo: ms-inventario
- [x] T-05 · `GET /products/{productId}/stock/movements` · cubre 014-AC-10 · nivel: it · módulo: ms-inventario
- [x] T-06 · Concurrencia: 50 `PUT` a la vez, misma key y `PUT` contra reservas · cubre 014-AC-08, 014-AC-09 · nivel: concurrencia · módulo: ms-inventario
- [x] T-07 · Logs del cambio · cubre 014-AC-11 · nivel: it · módulo: ms-inventario
