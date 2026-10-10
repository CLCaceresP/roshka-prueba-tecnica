# 015 · Plan

| Pieza | Dónde | Qué |
|---|---|---|
| `OpenApiConfiguration` | `configuracion` de cada servicio | Título, versión, descripción, `servers` (el gateway) y esquemas compartidos (`ProblemDetail`). |
| Anotaciones `@Tag` / `@Operation` / `@ApiResponse` / `@Parameter` / `@Schema` | controllers y DTO de `adaptadores/entrada/rest` | La documentación vive en el adaptador; el dominio y la aplicación no dependen de springdoc (ArchUnit). |
| Respuestas tipadas | controllers | Los `POST` devuelven `ResponseEntity<String>` por cómo se reproducen las respuestas almacenadas; se documenta el esquema con `@ApiResponse(content = @Content(schema = ...))`. |
| Dependencia springdoc en el gateway | `gateway-pedidos-inventario/pom.xml` | `springdoc-openapi-starter-webflux-ui`, versión verificada contra la fuente oficial (ADR 0023). |
| Rutas `/docs/**` | `application.yml` del gateway | Exponen `/v3/api-docs` de cada servicio y el Swagger UI con dos definiciones. |
| `ContratoOpenApiIT` | tests de cada servicio | Recorre el documento y verifica las reglas RN-07. |
| `DocumentacionGatewayIT` | tests del gateway | `GET /docs` y las definiciones, con backends simulados. |

## Estrategia de tests

| Criterio | Nivel |
|---|---|
| 015-AC-01 a 015-AC-04, 015-AC-07 | it (`ContratoOpenApiIT` en ambos servicios) |
| 015-AC-05, 015-AC-06 | it (`DocumentacionGatewayIT`) |
