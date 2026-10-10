-- Order aggregate. The version column backs optimistic locking; every transaction that writes to
-- the outbox increments it, so two overlapping transactions on one order cannot both commit
-- (ADR 0003, invariant 6).
create table orders (
  id                uuid primary key,
  status            varchar(16) not null
                      check (status in ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
  rejection_reason  varchar(32),
  correlation_id    varchar(100) not null,
  saga_started_at   timestamptz not null,
  version           bigint not null,
  created_at        timestamptz not null,
  updated_at        timestamptz not null,
  constraint orders_rejection_reason check ((status = 'REJECTED') = (rejection_reason is not null))
);
create index orders_pending_saga on orders (saga_started_at) where status = 'PENDING';

-- The composite primary key also enforces "one line per product" (spec 002, RN-04).
create table order_lines (
  order_id    uuid not null references orders (id),
  product_id  uuid not null,
  quantity    integer not null check (quantity between 1 and 1000),
  primary key (order_id, product_id)
);
