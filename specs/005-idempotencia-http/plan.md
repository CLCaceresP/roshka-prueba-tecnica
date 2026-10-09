# 005 · Plan técnico

## Diseño (recomendado en P-01)

```
Controller ──► IdempotentExecution (application, port.in genérico)
                 1. validar key y calcular request_hash
                 2. tryLock(idem:{service}:{key})        ── no → 409 + Retry-After
                 3. buscar key vigente
                      existe, mismo hash  → replay
                      existe, otro hash   → 422
                 4. @Transactional:
                      insert idempotency_keys ... on conflict do nothing
                        0 filas → otra transacción ganó → rollback → replay (paso 3)
                      ejecutar el caso de uso (efecto + outbox)
                      update idempotency_keys set response_* (solo si 2xx)
                 5. unlock (finally)
```

Si el caso de uso falla, el rollback también deshace el insert de la key (RN-06).

`IdempotentExecution` es un componente de `aplicacion` que envuelve a cualquier caso de uso de escritura. No es un filtro HTTP: el filtro no puede participar de la transacción del caso de uso. Se implementa una vez por servicio. No se extrae a un módulo compartido para no acoplar los servicios más allá del contrato; si la duplicación resulta significativa, se reevalúa con un ADR.

## Migración (en cada servicio que tiene escrituras)

```sql
create table idempotency_keys (
  key               varchar(100) primary key,
  request_hash      char(64)     not null,
  response_code     smallint,
  response_headers  jsonb,
  response_body     jsonb,
  created_at        timestamptz  not null,
  expires_at        timestamptz  not null
);
create index idempotency_keys_expires_at on idempotency_keys (expires_at);
```

## Configuración

```yaml
idempotencia:
  ttl: 24h
  reintentar-despues: 1s
  espera-bloqueo: 0ms   # en HTTP no se espera: el duplicado concurrente recibe 409 de inmediato
  lease-bloqueo: 10s
```

## Tests

| Criterio | Nivel |
|---|---|
| RN-01, RN-02 (hash canónico) | unit |
| AC-01..05, 08..11 | it (Testcontainers Postgres + Redis) |
| AC-06, 07 | concurrency |

## ADRs relacionados

[0005](../../docs/adr/0005-bloqueo-distribuido-como-defensa-en-profundidad.md), [0007](../../docs/adr/0007-gateway-delgado.md). Por escribir en Fase 3: idempotencia HTTP, que resuelve P-01.
