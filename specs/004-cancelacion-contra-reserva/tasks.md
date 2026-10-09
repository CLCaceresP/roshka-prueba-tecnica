# 004 · Tareas

Fase 2 (inventory):

- [x] T-01 · Tabla reserva × comando en el dominio (`Reserva`) · cubre RN-10, RN-11 · nivel: unit · módulo: ms-inventario
- [x] T-02 · `LiberarStock` sin reserva crea un tombstone y `R` posterior se rechaza · cubre 004-AC-05 · nivel: unit + it · módulo: ms-inventario
- [x] T-03 · `RESERVED` + `L` devuelve el stock una vez; `L` repetido → `NADA_QUE_LIBERAR` · cubre 004-AC-11 · nivel: unit + it · módulo: ms-inventario
- [x] T-04 · Dos `L` concurrentes en 2 instancias · cubre 004-AC-11 · nivel: concurrency · módulo: ms-inventario

Fase 3 (order):

- [x] T-05 · Tabla estado × entrada en el dominio (`Pedido`) · cubre RN-02..RN-09 · nivel: unit · módulo: ms-pedidos
- [x] T-06 · Orquestador emite la compensación ante `SR` tardío · cubre 004-AC-04, 004-AC-06 · nivel: unit · módulo: ms-pedidos
- [x] T-07 · `DELETE /orders/{id}`: 200 / 200 idempotente / 409 / 404 · cubre 004-AC-03, 004-AC-08, 004-AC-09 · nivel: it · módulo: ms-pedidos

Fase 4 (carrera y resiliencia):

- [x] T-08 · Cancelación antes de la reserva · cubre 004-AC-01 · nivel: it · módulo: ms-pedidos
- [x] T-09 · Cancelación concurrente con `SR` (con y sin lock) · cubre 004-AC-02 · nivel: concurrency · módulo: ms-pedidos
- [x] T-10 · Timeout concurrente con `SR` · cubre 004-AC-07 · nivel: concurrency · módulo: ms-pedidos
- [x] T-11 · `SJ` sobre `CONFIRMED` → DLT · cubre 004-AC-12 · nivel: it · módulo: ms-pedidos
- [x] T-12 · Duplicados en todas las secuencias · cubre 004-AC-10 · nivel: it · módulo: ms-pedidos
- [x] T-13 · `ConsistencyAssertions` (C-1..C-5) y propiedad con semilla · cubre 004-AC-13 · nivel: concurrency · módulo: ms-pedidos
