-- Stock invariants are enforced here, not only in code (ADR 0009, invariants 1 and 2).
create table products (
  id          uuid primary key,
  name        varchar(120) not null,
  total       integer not null check (total >= 0),
  available   integer not null check (available >= 0),
  reserved    integer not null check (reserved >= 0),
  created_at  timestamptz not null,
  constraint products_stock_conservation check (available + reserved = total)
);

-- One reservation per order; the row serializes every message of the order (ADR 0010).
-- A RELEASED reservation without lines is a tombstone.
create table reservations (
  order_id              uuid primary key,
  status                varchar(16) not null check (status in ('RESERVED', 'RELEASED', 'REJECTED')),
  rejection_reason      varchar(32),
  rejected_product_ids  uuid[] not null default '{}',
  created_at            timestamptz not null,
  updated_at            timestamptz not null,
  constraint reservations_rejection_reason
    check ((status = 'REJECTED') = (rejection_reason is not null))
);

-- No foreign key to products: a reservation rejected as UNKNOWN_PRODUCT keeps the requested lines.
create table reservation_lines (
  order_id    uuid not null references reservations (order_id),
  product_id  uuid not null,
  quantity    integer not null check (quantity > 0),
  primary key (order_id, product_id)
);
