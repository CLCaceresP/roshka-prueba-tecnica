-- HTTP idempotency (ADR 0011). A row only becomes visible committed together with the effect of
-- its request, so there is no in-progress status. The body is text, not jsonb, so that replays are
-- byte-for-byte identical.
create table idempotency_keys (
  key               varchar(100) primary key,
  request_hash      char(64) not null,
  response_code     smallint,
  response_headers  jsonb,
  response_body     text,
  created_at        timestamptz not null,
  expires_at        timestamptz not null
);
create index idempotency_keys_expires_at on idempotency_keys (expires_at);
