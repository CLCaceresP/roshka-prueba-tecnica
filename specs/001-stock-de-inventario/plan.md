# 001 · Plan técnico

## Capas

| Capa | Elementos |
|---|---|
| `dominio` | `Producto` (invariantes RN-01/02), `Reserva` (estados y transiciones), `LineaReserva`, `MotivoRechazo`, `RespuestaReserva`, excepciones `ProductoInvalido` y `ProductoNoEncontrado` |
| `aplicacion` | `RegistrarProducto` (`puertos.entrada`), `ObtenerStockProducto` (`puertos.entrada`), `ReservarStock` y `LiberarStock` (casos de uso invocados desde los listeners de la 003), puertos `ProductoRepository`, `ReservaRepository`, `LibroStock` (descuento condicional), `MensajesProcesados` y `OutboxMensajes` |
| `adaptadores.entrada.rest` | `ProductoController`, DTOs como records, `ProblemDetail` mapper |
| `adaptadores.salida.persistence` | `JdbcProductoRepository`, `JdbcReservaRepository`, `JdbcLibroStock` con el `UPDATE` condicional (JDBC, no JPA: ver ADR 0009) |

## Migración `V1__inventory_schema.sql`

```sql
create table products (
  id          uuid primary key,
  name        varchar(120) not null,
  total       integer not null check (total >= 0),
  available   integer not null check (available >= 0),
  reserved    integer not null check (reserved >= 0),
  created_at  timestamptz not null,
  constraint products_stock_conservation check (available + reserved = total)
);

create table reservations (
  order_id          uuid primary key,
  status            varchar(16) not null check (status in ('RESERVED','RELEASED','REJECTED')),
  rejection_reason  varchar(32),
  rejected_product_ids uuid[] not null default '{}',
  created_at        timestamptz not null,
  updated_at        timestamptz not null
);

create table reservation_lines (
  order_id    uuid not null references reservations(order_id),
  product_id  uuid not null,
  quantity    integer not null check (quantity > 0),
  primary key (order_id, product_id)
);
```

`reservation_lines.product_id` no tiene FK hacia `products`: una reserva rechazada por `UNKNOWN_PRODUCT` guarda las líneas pedidas para auditoría.

## Reserva atómica

Una transacción:

1. Inserta la reserva. La PK de `order_id` serializa los intentos concurrentes sobre el mismo pedido (ver 004).
2. Ordena las líneas por `productId` ascendente.
3. Por cada línea: `update products set available = available - :q, reserved = reserved + :q where id = :id and available >= :q`.
4. Si alguna línea afecta 0 filas: se distingue entre `UNKNOWN_PRODUCT` (no existe) e `INSUFFICIENT_STOCK`. Se hace **rollback del descuento** y la reserva se registra `REJECTED` con su motivo. Se implementa con la operación inversa en la misma transacción, que ya tiene los locks de fila de esos productos (ADR 0009).

## Tests

| Criterio | Nivel |
|---|---|
| AC-01, 02, 03, 11 | IT web (Testcontainers Postgres) + unit del caso de uso |
| AC-04, 05, 06, 08 | Unit (dominio y caso de uso con fakes) + IT del `JdbcLibroStock` (`InventarioPersistenciaIT`) |
| AC-07 | IT de esquema (SQL directo): `InventarioPersistenciaIT` |
| AC-09, 10 | `*ConcurrencyIT` con `@Tag("concurrency")` |

## ADRs relacionados

- [0009](../../docs/adr/0009-anti-sobreventa-en-sql.md): garantía anti-sobreventa en SQL y orden de actualización.
- [0005](../../docs/adr/0005-bloqueo-distribuido-como-defensa-en-profundidad.md): el lock no es la barrera de corrección.
