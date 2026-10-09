# 005 · Tareas

Fase 2 (inventory, `POST /products`):

- [x] T-01 · Validación de la key y hash canónico · cubre 005-AC-01, RN-02 · nivel: unit · módulo: ms-inventario
- [x] T-02 · Replay, 422 y errores no almacenados en `POST /products` · cubre 005-AC-03, 005-AC-08, 005-AC-10 · nivel: it · módulo: ms-inventario

Fase 3 (order):

- [x] T-03 · `IdempotentExecution` con lock Redisson y fallback a PK · cubre 005-AC-02, 005-AC-05 · nivel: it · módulo: ms-pedidos
- [x] T-04 · Key reutilizada en otra ruta → 422 · cubre 005-AC-04 · nivel: it · módulo: ms-pedidos
- [x] T-05 · Vencimiento y purga · cubre 005-AC-09 · nivel: unit + it · módulo: ms-pedidos
- [x] T-06 · Cancel idempotente · cubre 005-AC-11 · nivel: it · módulo: ms-pedidos

Fase 4:

- [x] T-07 · 50 requests concurrentes con la misma key en 2 instancias · cubre 005-AC-06 · nivel: concurrency · módulo: ms-pedidos
- [x] T-08 · Lo mismo con el lock deshabilitado · cubre 005-AC-07 · nivel: concurrency · módulo: ms-pedidos
