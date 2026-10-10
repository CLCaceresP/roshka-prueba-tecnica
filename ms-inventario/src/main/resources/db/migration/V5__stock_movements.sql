-- Historial de ajustes de stock (spec 014, ADR 0022). Cada fila se inserta en la misma transaccion
-- que el UPDATE del producto y guarda el stock que quedo despues del ajuste.
create table stock_movements (
  id               uuid primary key,
  product_id       uuid not null references products (id),
  type             varchar(8) not null check (type in ('ADD', 'REMOVE')),
  quantity         integer not null check (quantity > 0),
  reason           varchar(200),
  total_after      integer not null check (total_after >= 0),
  available_after  integer not null check (available_after >= 0),
  created_at       timestamptz not null
);
create index stock_movements_product_created on stock_movements (product_id, created_at desc, id desc);
