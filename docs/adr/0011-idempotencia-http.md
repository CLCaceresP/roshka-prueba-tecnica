# 0011 · Idempotencia HTTP: key y efecto en una sola transacción, lock como primera barrera

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-09, REQ-15, REQ-24, REQ-25

## Contexto y problema

Los endpoints de escritura (`POST /products`, `POST /orders`, `DELETE /orders/{id}`) deben ser idempotentes (REQ-24). Un cliente que reintenta tras un timeout no puede crear dos recursos, y dos copias del mismo request pueden llegar a la vez a instancias distintas. La spec 005 fija el comportamiento observable: replay, `422` y `409` con `Retry-After`. Este ADR fija cómo se garantiza.

## Factores de decisión

- No puede existir un efecto sin su key ni una key sin su efecto.
- Correcto aunque el lock distribuido falle ([0005](0005-bloqueo-distribuido-como-defensa-en-profundidad.md)).
- El replay devuelve exactamente la misma respuesta.

## Opciones consideradas

1. **Dos fases:** insertar la key `IN_PROGRESS`, commitear, ejecutar y actualizar a `COMPLETED`. El estado "en proceso" es visible, pero si el proceso cae entre fases la key queda colgada y hay que vencerla. Además, el efecto y el `COMPLETED` quedan en transacciones distintas.
2. **Filtro HTTP con caché en Redis.** No participa de la transacción del caso de uso: el efecto y la respuesta guardada pueden divergir.
3. **Una sola transacción:** reclamar la key, ejecutar el caso de uso y guardar la respuesta, con el lock Redisson alrededor.

## Decisión

**Opción 3.**

```
lock idem:{service}:{key}  (tryLock, espera 0 por defecto) ── no → 409 + Retry-After
  key vigente con el mismo hash → replay      · con otro hash → 422
  @Transactional
    insert into idempotency_keys ... on conflict (key) do update ... where expires_at <= now
      0 filas → otra transacción ganó la key → rollback → replay
    caso de uso (efecto + outbox)
    update idempotency_keys set response_* ...
unlock
```

- Tabla `idempotency_keys(key PK, request_hash, response_code, response_headers jsonb, response_body text, created_at, expires_at)`. **Sin columna `status`**: con una sola transacción, una fila "en proceso" nunca es visible para otra (decisión de la spec 005, P-01).
- `response_body` es `text` y no `jsonb`: `jsonb` reordena claves y normaliza espacios, y el replay no sería byte a byte idéntico.
- Solo se guardan las respuestas 2xx. Un error implica rollback: no hubo efecto y el cliente puede corregir y reintentar con la misma key.
- `request_hash` = SHA-256 de método, ruta y body ya parseado y serializado de forma canónica. Diferencias de espacios u orden de claves en el JSON original no cambian el hash.
- Si el lock falla, la PK de `idempotency_keys` serializa: el segundo request espera a que el primero commitee y luego hace replay. El resultado es correcto, solo más lento.
- El componente vive en `aplicacion/idempotencia` de cada servicio (puertos `AlmacenClavesIdempotencia` y `BloqueoDistribuido`). No se comparte entre servicios para no acoplarlos más allá del contrato de mensajes.

## Consecuencias

- Positivas: atomicidad total entre efecto y key; correcto sin Redis; replay exacto.
- Negativas: el código se duplica en los dos servicios. La tabla crece hasta la purga por `expires_at`.
- Riesgos: un caso de uso lento mantiene la fila reclamada y bloquea a los duplicados que llegan sin lock. Lo acota el timeout de la transacción.

## Cómo se verifica

- Unitarios de `ProcesadorSolicitudIdempotente` con fakes: replay, 422, 409, errores no guardados, carrera perdida → replay, key vencida.
- IT de la API: replay byte a byte, `Idempotent-Replayed`, 422, 409 con lock tomado, key vencida.
- Concurrencia: 50 requests con la misma key, con y sin lock (spec 005, AC-06/07).
