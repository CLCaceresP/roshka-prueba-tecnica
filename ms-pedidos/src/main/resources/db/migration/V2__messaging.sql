-- Idempotent consumer: the insert and the business effect share a transaction (ADR 0010).
create table processed_messages (
  message_id    uuid primary key,
  processed_at  timestamptz not null
);

-- Transactional outbox, published in id order by a single publisher (ADR 0003).
create table outbox (
  id            bigint generated always as identity primary key,
  message_id    uuid not null unique,
  aggregate_id  uuid not null,
  topic         varchar(100) not null,
  type          varchar(50) not null,
  payload       jsonb not null,
  headers       jsonb not null default '{}',
  created_at    timestamptz not null,
  published_at  timestamptz
);
create index outbox_unpublished on outbox (id) where published_at is null;

-- ShedLock: at most one outbox publisher runs at a time across instances.
create table shedlock (
  name        varchar(64) primary key,
  lock_until  timestamp not null,
  locked_at   timestamp not null,
  locked_by   varchar(255) not null
);
