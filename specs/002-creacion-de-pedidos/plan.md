# 002 · Plan técnico

## Capas

| Capa | Elementos |
|---|---|
| `dominio` | `Pedido` (agregado), `EstadoPedido` con comportamiento por estado (patrón State: un tipo por estado que decide qué eventos acepta), `LineaPedido`, `MotivoRechazo`, `TransicionPedidoInvalida`, eventos de dominio (`PedidoColocado`, `PedidoConfirmado`, `PedidoRechazado`, `PedidoCancelado`) |
| `aplicacion` | `ColocarPedido` y `ObtenerPedido` (`puertos.entrada`); puertos `PedidoRepository` y `Outbox` (`puertos.salida`). `ColocarPedido` traduce `PedidoColocado` a un `ReservarStock` del contrato |
| `adaptadores.entrada.rest` | `PedidoController`, DTOs y traducción de excepciones a `ProblemDetail` |
| `adaptadores.salida.persistence` | Entidad JPA `PedidoEntity` con `@Version`, `OrderLineEntity`, `OutboxEntity`, mappers |
| `adaptadores.salida.messaging` | Publisher del outbox (ShedLock), detallado en la 003 |

## Migración `V1__orders_schema.sql`

```sql
create table orders (
  id                uuid primary key,
  status            varchar(16) not null check (status in ('PENDING','CONFIRMED','REJECTED','CANCELLED')),
  rejection_reason  varchar(32),
  saga_started_at   timestamptz not null,
  version           bigint not null default 0,
  created_at        timestamptz not null,
  updated_at        timestamptz not null,
  constraint orders_rejection_reason check ((status = 'REJECTED') = (rejection_reason is not null))
);

create table order_lines (
  order_id    uuid not null references orders(id),
  product_id  uuid not null,
  quantity    integer not null check (quantity between 1 and 1000),
  primary key (order_id, product_id)
);

create table outbox (
  id            bigint generated always as identity primary key,
  message_id    uuid not null unique,
  aggregate_id  uuid not null,
  topic         varchar(100) not null,
  type          varchar(50) not null,
  payload       jsonb not null,
  headers       jsonb not null,
  created_at    timestamptz not null,
  published_at  timestamptz
);
create index outbox_unpublished on outbox (id) where published_at is null;
```

La PK compuesta de `order_lines` hace cumplir RN-04 también en la base.

## Domain events → mensajes

El agregado registra los eventos de dominio. El caso de uso los traduce a mensajes del contrato y los guarda en el outbox dentro de la misma transacción. En esa transacción se fuerza el incremento de versión del `Pedido` ([ADR 0003](../../docs/adr/0003-outbox-con-polling-y-shedlock.md)).

## Tests

| Criterio | Nivel |
|---|---|
| AC-07 | Unit del dominio: matriz completa de estados × entradas |
| AC-01, 04, 05, 06 | IT web (Testcontainers Postgres) |
| AC-02, 03 | IT de persistencia (transacción y rollback) |

## ADRs relacionados

- Por escribir en Fase 3: máquina de estados de `Pedido` (patrón State frente a un `switch` o una tabla de transiciones).
- [0003](../../docs/adr/0003-outbox-con-polling-y-shedlock.md), [0004](../../docs/adr/0004-saga-orquestada-dentro-de-ms-pedidos.md).
