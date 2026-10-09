# 002 · Tareas

- [x] T-01 · `Pedido` nace en `PENDING` y registra `PedidoColocado` · cubre 002-AC-01 · nivel: unit · módulo: ms-pedidos
- [x] T-02 · Matriz de transiciones de `Pedido` (State) · cubre 002-AC-07 · nivel: unit · módulo: ms-pedidos
- [x] T-03 · Validaciones de líneas (1..50, 1..1000, sin duplicados) · cubre 002-AC-06 · nivel: unit · módulo: ms-pedidos
- [x] T-04 · `ColocarPedido` guarda el pedido y `ReservarStock` en el outbox atómicamente · cubre 002-AC-02 · nivel: unit + it · módulo: ms-pedidos
- [x] T-05 · Rollback: sin pedido no hay outbox · cubre 002-AC-03 · nivel: it · módulo: ms-pedidos
- [x] T-06 · `POST /orders` → 202 + Location; validación → 400 · cubre 002-AC-01, 002-AC-06 · nivel: it · módulo: ms-pedidos
- [x] T-07 · `GET /orders/{id}` → 200 / 404 · cubre 002-AC-04, 002-AC-05 · nivel: it · módulo: ms-pedidos
