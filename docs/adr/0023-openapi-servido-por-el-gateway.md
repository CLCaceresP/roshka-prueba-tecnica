# 0023 · OpenAPI servido por el gateway

- Estado: Aceptado
- Fecha: 2026-10-10
- Requisitos: EXT-04, REQ-24, BON-02

## Contexto y problema

La documentación OpenAPI vive en cada servicio y hoy se abre por los puertos de los servicios, que se saltan el rate limit y el filtro de `Idempotency-Key` del gateway. La documentación debe estar completa en el gateway, que es el único punto de entrada.

## Factores de decisión

- «Try it out» debe usar el camino real (gateway con su rate limit y su filtro de la key).
- El gateway sigue siendo delgado: sin lógica de negocio (ADR 0007).
- No se agregan componentes de infraestructura nuevos sin preguntar.
- Cada servicio es dueño de la documentación de su API.

## Opciones consideradas

1. **El gateway enruta los documentos de los servicios y sirve un Swagger UI con dos definiciones (elegida).** La documentación sigue naciendo en cada servicio; el gateway solo la expone.
2. **Un documento fusionado a mano en el gateway.** Se desfasa de la API real.
3. **Un contenedor Swagger UI aparte.** Es un componente de infraestructura nuevo.
4. **Dejar el Swagger por servicio.** No cumple lo pedido y deja «Try it out» fuera del gateway.

## Decisión

- El gateway incorpora `springdoc-openapi-starter-webflux-ui` (versión verificada contra la fuente oficial y compatible con su versión de Spring Boot; se registra en el ADR 0008) y expone `/docs`.
- Rutas del gateway: `/docs/ms-pedidos/v3/api-docs` y `/docs/ms-inventario/v3/api-docs` hacia el `/v3/api-docs` de cada servicio. Los `servers` de cada documento apuntan al gateway.
- Las anotaciones OpenAPI viven en los adaptadores REST de cada servicio; ArchUnit sigue prohibiendo springdoc en `dominio` y `aplicacion`.
- Versión verificada: springdoc 3.1.1 (la última publicada en Maven Central, con las correcciones de seguridad de 3.1.1) está compilada contra Spring Boot 4.1.0; en el gateway, que usa Boot 4.0.8, arranca y sirve `/docs` sin problemas (`DocumentacionGatewayIT`).
- Un test de contrato por servicio verifica que todo está documentado y que los `code` documentados existen.

## Consecuencias

**Positivas**
- Un solo lugar para explorar y probar toda la API, por el camino real.
- La documentación no se desfasa: sale del código de cada servicio.

**Negativas y riesgos**
- Una dependencia nueva en el gateway; se verifica la compatibilidad con Spring Cloud Gateway antes de fijarla.
- Si un servicio está caído, su definición no carga en el Swagger del gateway.
- Anotar los controllers los llena de metadatos; se mitiga agrupándolos en interfaces de documentación del adaptador si el ruido crece.

## Cómo se verifica
- Spec 015: tests de contrato en los servicios y `DocumentacionGatewayIT`.

## Referencias
- [Spec 015](../../specs/015-documentacion-openapi/spec.md) · [ADR 0007](0007-gateway-delgado.md) · [ADR 0008](0008-plataforma-y-versiones.md)
