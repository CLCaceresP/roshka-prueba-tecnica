# 003 · Tareas

Fase 2 (inventory):

- [x] T-01 · Records del contrato y envelope en `contrato-eventos` · cubre 003-AC-06, 003-AC-07 · nivel: unit · módulo: contrato-eventos
- [x] T-02 · `ManejarReservarStock` deduplica por `idMensaje` · cubre 003-AC-04 · nivel: unit + it · módulo: ms-inventario
- [x] T-03 · `ManejarReservarStock` responde una vez con causation/correlation · cubre 003-AC-06 · nivel: unit · módulo: ms-inventario
- [x] T-04 · Fallo antes del commit no deja efecto parcial · cubre 003-AC-08 · nivel: it · módulo: ms-inventario
- [x] T-05 · `OutboxPublisher` publica en orden, una vez, con key = orderId · cubre 003-AC-07, 003-AC-09 · nivel: it · módulo: ms-inventario

Fase 3 (order):

- [x] T-06 · Orquestador: `StockReservado` → `CONFIRMED`; `StockRechazado` → `REJECTED` · cubre 003-AC-01, 003-AC-02, 003-AC-03 · nivel: unit · módulo: ms-pedidos
- [x] T-07 · Consumidor de `inventario.eventos` idempotente · cubre 003-AC-05 · nivel: it · módulo: ms-pedidos
- [x] T-08 · `OutboxPublisher` de order · cubre 003-AC-09 · nivel: it · módulo: ms-pedidos
- [x] T-09 · `TimeoutSagaJob` → `REJECTED(TIMEOUT)` + `LiberarStock` · cubre 003-AC-10 · nivel: unit + it · módulo: ms-pedidos
- [x] T-10 · Camino feliz y rechazo de punta a punta · cubre 003-AC-01, 003-AC-02, 003-AC-03 · nivel: it · módulo: ms-pedidos

Fase 4 (resiliencia):

- [x] T-11 · `ErrorClassification` (Strategy) · cubre 003-AC-11, 003-AC-12 · nivel: unit · módulo: ambos
- [x] T-12 · Reintento bloqueante preserva el orden · cubre 003-AC-11 · nivel: it · módulo: ms-inventario
- [x] T-13 · Mensaje no procesable → DLT + métrica · cubre 003-AC-12 · nivel: it · módulo: ambos
- [x] T-14 · Dos instancias por consumer group · cubre 003-AC-13 · nivel: concurrency · módulo: ms-pedidos

Endurecimiento de publicación y recuperación (ADR 0014):

- [x] T-15 · Renovar el lock del publisher antes de cada fila, detener el lote al perderlo y acotar `max.block.ms` · cubre 003-AC-14, REQ-06, REQ-26 · nivel: it · módulo: ambos
- [x] T-16 · Contar y registrar solo publicaciones DLT confirmadas; propagar fallos de envío · cubre 003-AC-15, REQ-18 · nivel: unit · módulo: ambos
- [x] T-17 · Documentar y probar el reproceso del `LiberarStock` original después de agotar los reintentos · cubre 003-AC-16, REQ-12, REQ-26, REQ-27 · nivel: it · módulo: ms-inventario
