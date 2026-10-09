# 0007 · API Gateway delgado con Spring Cloud Gateway

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-02, REQ-24, BON-02, BON-03

## Contexto y problema

El enunciado define endpoints en cada servicio y no exige un gateway. Con dos réplicas por servicio, el evaluador necesita un único punto de entrada para Postman y k6. Además, hay preocupaciones transversales que conviene resolver una sola vez:

- un id de correlación;
- el inicio de la traza;
- el rate limiting;
- el rechazo temprano de escrituras sin `Idempotency-Key`.

El riesgo de un gateway es que termine absorbiendo lógica de negocio y se vuelva un monolito distribuido.

## Factores de decisión

- Un único punto de entrada balanceado sobre las réplicas.
- La traza nace en el borde y abarca toda la saga (BON-03).
- Cero lógica de negocio fuera de los servicios.

## Opciones consideradas

1. **Sin gateway.** Los clientes llaman a los servicios directamente. Con réplicas necesita un balanceador igual y reparte las preocupaciones transversales entre servicios.
2. **Spring Cloud Gateway delgado.**
3. **Gateway con lógica** (agregación, validación de body, orquestación). Acopla el borde con el dominio.
4. **Nginx/Envoy.** Sirven como proxy, pero integrar el rate limiting con Redis, el `X-Correlation-Id` y la traza con Micrometer exige más configuración externa al stack Java.

## Decisión

**Opción 2.** Responsabilidades del gateway, **y solo estas**:

- Ruteo de `/orders/**` a ms-pedidos y de `/products/**` a ms-inventario, balanceando entre réplicas.
- Generar `X-Correlation-Id` si falta y propagarlo.
- Rechazar con `400` (`ProblemDetail`) los `POST` sin `Idempotency-Key`. Solo verifica su **presencia**; no valida el body ni consulta estado.
- Rate limiting con Redis (`RequestRateLimiter`), con límites en `application.yml`.
- Iniciar y propagar el trace context (W3C `traceparent`).

Los servicios **también** validan `Idempotency-Key` y siguen siendo la fuente de verdad de la idempotencia. Se pueden invocar directamente (tests, diagnóstico), así que el gateway es solo un rechazo temprano.

## Consecuencias

- Positivas: un punto de entrada para el evaluador; la traza empieza en el borde; la lógica sigue en los servicios.
- Negativas: un salto de red y un contenedor más. El rate limiting depende de Redis, igual que los locks ([0005](0005-bloqueo-distribuido-como-defensa-en-profundidad.md)).
- Riesgos: la tentación de agregar lógica. Lo controla `architecture-reviewer`, y el gateway no depende de módulos de dominio.

## Cómo se verifica

- Tests del gateway: ruteo, generación y propagación de `X-Correlation-Id`, `400` sin `Idempotency-Key`, `429` al superar el límite.
- Una traza en Jaeger que va del gateway a order, inventory y de vuelta a order (spec 006).

## Referencias

- Spring Cloud Gateway, "RequestRateLimiter GatewayFilter Factory".
