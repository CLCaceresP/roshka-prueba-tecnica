# 0009 · La base de datos garantiza la no sobreventa: UPDATE condicional, CHECK y orden de actualización

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-10, REQ-11, REQ-13, REQ-25, REQ-31

## Contexto y problema

Varias instancias de ms-inventario procesan reservas concurrentes sobre los mismos productos. El enunciado exige no vender más unidades de las disponibles y que el stock nunca quede negativo (REQ-13). El patrón ingenuo "leer el stock, comparar en memoria y escribir" pierde actualizaciones bajo concurrencia, aun con un lock distribuido: el lock puede vencer ([0005](0005-bloqueo-distribuido-como-defensa-en-profundidad.md)).

## Factores de decisión

- La corrección no puede depender de la coordinación entre instancias.
- Una reserva de varias líneas es todo o nada (REQ-11).
- Sin deadlocks entre reservas con líneas cruzadas.

## Opciones consideradas

1. **Leer, validar en memoria y escribir con `@Version` por producto.** Correcto, pero bajo contención alta (100 pedidos por las últimas 10 unidades) casi todas las transacciones fallan por versión y se reintentan. Mucho trabajo desperdiciado.
2. **`SELECT ... FOR UPDATE` del producto y luego `UPDATE`.** Correcto, pero son dos viajes a la base por línea y los locks se mantienen más tiempo.
3. **`UPDATE` condicional atómico + `CHECK` en la tabla.**

## Decisión

**Opción 3.**

```sql
update products
   set available = available - :qty, reserved = reserved + :qty
 where id = :id and available >= :qty
```

- Si afecta 1 fila, la línea quedó reservada. Si afecta 0, no hay stock suficiente o el producto no existe; se distinguen con una consulta posterior.
- **Constraints:** `check (available >= 0)`, `check (reserved >= 0)` y `check (available + reserved = total)`. Aun si un bug escribiera mal, la base lo rechaza.
- **Todo o nada:** las líneas de un pedido se aplican dentro de una sola transacción. Si una falla, las ya aplicadas se revierten en la misma transacción con la operación inversa (`available + q`, `reserved - q`), que es segura porque esa transacción ya tiene el lock de fila de esos productos.
- **Orden de actualización:** las líneas se procesan en **orden ascendente de `productId`**. Dos transacciones que tocan {A, B} toman los locks de fila en el mismo orden, así que no se forma un ciclo de espera (deadlock).
- ms-inventario usa **JDBC (`JdbcClient`) en lugar de JPA**. Todas sus escrituras relevantes son SQL condicional o inserts que serializan por PK, y JPA no aporta nada: su flush diferido haría menos explícito el orden de los statements, del que dependen [0003](0003-outbox-con-polling-y-shedlock.md) y [0010](0010-consumidor-idempotente-y-tombstone.md).

## Consecuencias

- Positivas: correcto con cualquier número de instancias; un solo viaje por línea; los rechazos no consumen reintentos.
- Negativas: el motivo exacto del rechazo (producto inexistente o stock insuficiente) requiere una consulta adicional, solo en el camino de rechazo.
- Riesgos: si alguien agrega una escritura de stock que no use este patrón, los `CHECK` la rechazan. `idempotency-reviewer` busca ese antipatrón.

## Cómo se verifica

- IT de esquema: un `UPDATE` directo que deja `available < 0` o rompe la conservación falla por el `CHECK`.
- `*ConcurrencyIT`: 100 reservas concurrentes por 10 unidades → exactamente 10 reservadas; 50 + 50 reservas con líneas cruzadas → todas terminan, sin deadlocks.
