# 015 · Documentación OpenAPI completa, servida por el gateway

Estado: Aprobada
Requisitos: EXT-04, REQ-24, REQ-19
Decisiones: [ADR 0023](../../docs/adr/0023-openapi-servido-por-el-gateway.md)

## Contexto

Hoy springdoc genera un documento sin anotaciones: las respuestas de los `POST` salen como `string`, `Idempotency-Key` aparece como opcional, no figuran los errores ni las cabeceras, y el gateway no sirve la documentación (se entra por los puertos de los servicios, saltándose el rate limit y el filtro de la key).

## Reglas de negocio

- **RN-01** Todos los endpoints de `ms-pedidos` y `ms-inventario` se documentan con `@Tag`, `@Operation` (resumen y descripción en español), parámetros, esquema de la solicitud y de la respuesta exitosa tipada (`PedidoResponse`, `ProductoResponse`, etc.).
- **RN-02** Cada endpoint documenta todos sus códigos de respuesta, cada error con el esquema `ProblemDetail` (que incluye la propiedad `code`) y un ejemplo con el `code` real.
- **RN-03** `Idempotency-Key` se documenta como **obligatoria** en todo endpoint de escritura, con su formato y las reglas de la invariante 12. Las cabeceras `Location`, `Retry-After` e `Idempotent-Replayed` figuran donde corresponde.
- **RN-04** Los parámetros de paginación y de fechas documentan sus valores por defecto, límites y formato (`MM/dd/yyyy`), con ejemplos.
- **RN-05** El gateway sirve la documentación: `GET /docs` abre un Swagger UI con dos definiciones, `ms-pedidos` y `ms-inventario`, tomadas de los servicios a través del gateway. Los `servers` apuntan al propio gateway, así que «Try it out» pasa por el rate limit y el filtro de la key.
- **RN-06** El gateway sigue sin lógica de negocio: solo expone los documentos de los servicios (ADR 0007).
- **RN-07** Un test verifica que cada ruta y método tiene respuestas documentadas, que todo `POST` declara `Idempotency-Key` obligatoria y que los códigos de error documentados son los que la API devuelve.

## Criterios de aceptación

### 015-AC-01 · Todos los endpoints están documentados (EXT-04)
- Given el documento OpenAPI de cada servicio
- When se recorren sus operaciones
- Then cada una tiene `summary`, `description`, `tags` y al menos una respuesta exitosa tipada

### 015-AC-02 · Los errores están documentados con ejemplos (REQ-19)
- Given una operación que puede fallar
- Then documenta cada `code` posible con el esquema `ProblemDetail` y un ejemplo

### 015-AC-03 · La idempotencia está documentada (REQ-24)
- Given una operación de escritura
- Then declara `Idempotency-Key` como cabecera obligatoria y documenta `409`, `422` y la cabecera `Retry-After`

### 015-AC-04 · Los parámetros de listados están documentados (EXT-01, EXT-02)
- Given `GET /products`, `GET /orders` y `GET .../stock/movements`
- Then documentan `page`, `size`, y en pedidos `from` y `to` con formato, defecto y límites

### 015-AC-05 · El gateway sirve la documentación (EXT-04)
- Given el gateway en marcha
- When llega `GET /docs`
- Then responde el Swagger UI con las definiciones de `ms-pedidos` y de `ms-inventario`
- And las definiciones se obtienen por rutas del gateway y sus `servers` apuntan al gateway

### 015-AC-06 · Probar desde Swagger usa el camino real (REQ-24)
- Given una escritura probada desde el Swagger del gateway sin `Idempotency-Key`
- Then el gateway la rechaza con `400 IDEMPOTENCY_KEY_MISSING`

### 015-AC-07 · La documentación no se desvía de la API (REQ-24)
- Given los endpoints implementados
- Then el test de contrato falla si una operación no está documentada o si documenta un `code` que ninguna prueba devuelve

## Fuera de alcance
- Un portal de documentación aparte del Swagger UI y la generación de clientes.

## Preguntas abiertas
Ninguna.
