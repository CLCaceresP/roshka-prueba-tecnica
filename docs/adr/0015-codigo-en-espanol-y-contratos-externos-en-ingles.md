# 0015 · Código en español; contratos externos y datos persistidos en inglés

- Estado: Aceptado
- Fecha: 2026-10-10
- Requisitos: REQ-01, REQ-16, REQ-19, REQ-20, REQ-21, REQ-22, REQ-23, REQ-24

## Contexto y problema

El sistema se documenta y se evalúa en español. Hace falta fijar un único idioma para identificadores, tests, mensajes y configuración, y decidir qué partes no se pueden traducir sin romper el enunciado o los datos: los endpoints `POST /orders`, `GET /orders/{orderId}`, `DELETE /orders/{orderId}`, `POST /products` y `GET /products/{productId}/stock` (REQ-19 a REQ-23), los estados `PENDING`, `CONFIRMED`, `REJECTED` y `CANCELLED` del pedido, y el esquema de Postgres, cuyas migraciones ya commiteadas son inmutables (invariante 14).

## Factores de decisión

- Que el enunciado y su evaluación funcionen sin adaptaciones (REQ-19 a REQ-23).
- Respetar la inmutabilidad de las migraciones y no introducir migraciones solo por idioma.
- Que el código sea uniforme: un único idioma para identificadores, tests, comentarios y mensajes.
- Que el vocabulario del dominio sea el mismo en el código, en las specs y en los ADR.

## Opciones consideradas

1. **Traducirlo todo, incluido el API HTTP y la base de datos.** Cambia los paths y los estados que fija el enunciado y obliga a migraciones de renombrado. Descartada: rompe REQ-19 a REQ-23 y el enunciado gana sobre cualquier convención interna.
2. **Todo el código en inglés.** Es lo habitual, pero deja la documentación y el código en idiomas distintos. Descartada.
3. **Código y contratos internos en español; API HTTP, estados, valores persistidos y esquema en inglés.** Elegida.
4. **Código en español, pero contrato de mensajes (tópicos y JSON de eventos) en inglés.** Descartada: el contrato de mensajes es interno al sistema, no hay consumidores externos, y dejarlo en inglés partiría el sistema en dos idiomas.

## Decisión

Se adopta la opción 3. Los parámetros que la hacen verificable:

- **Servicios y módulos Maven:** `ms-pedidos`, `ms-inventario`, `gateway-pedidos-inventario`, `contrato-eventos` y `pruebas-sistema`. El `artifactId` raíz es `pedidos-inventario`. Las bases son `orders_db` e `inventory_db`.
- **Paquetes** bajo `com.roshka.challenge`: `pedidos`, `inventario`, `contrato`, `gateway` y `sistema`; dentro de cada servicio `dominio`, `aplicacion`, `adaptadores.entrada`, `adaptadores.salida`, `configuracion`, `aplicacion.puertos.entrada|salida`. Sin tildes ni `ñ` en identificadores, paquetes ni archivos.
- **Vocabulario:** `docs/glosario.md`. Se conservan los nombres de patrones y frameworks (`Outbox`, `Saga`, `DLT`, `Controller`, `Listener`, `Handler`, `Repository`, `Properties`, `Configuration`, `Filter`, `Entity`, `Request`, `Response`, `Job`).
- **Tests:** `debe_<resultado>_cuando_<condicion>`.
- **Mensajería en español:** tópicos `inventario.comandos` e `inventario.eventos` (con sus `.DLT`), nombres de los mensajes (`ReservarStock`, `StockReservado`…) y campos del sobre y de las cargas (`idMensaje`, `tipo`, `versionEsquema`, `ocurridoEn`, `idCorrelacion`, `idCausa`, `idAgregado`, `carga`). `versionEsquema` vale 1.
- **Se queda en inglés:** paths, headers y campos JSON del API HTTP (los DTO usan `@JsonProperty`); los estados del enunciado y los valores de enums que se persisten o salen por HTTP; el campo `code` de los errores; tablas, columnas y nombres de base de datos (las entidades JPA llevan `@Column(name = ...)` explícito); nombres y etiquetas de métricas; claves del MDC de los logs.
- **Conversión entre enums:** los enums de dominio que se persisten tienen constantes en inglés y los del contrato están en español. Cuando las constantes difieren, la aplicación convierte con un `switch` exhaustivo (`MotivoRechazo` → `MotivoRechazoStock`); `valueOf(name())` solo se usa entre enums con constantes idénticas.
- **Configuración:** claves de `application.yml` del proyecto en español y kebab-case (`outbox.publicador.intervalo-sondeo`), iguales a los componentes de los records `*Properties`.
- **Textos:** comentarios, Javadoc, logs, mensajes de excepción y `title`/`detail` de `ProblemDetail` en español, con tildes.
- **Commits:** en español, con el tipo de Conventional Commits y las marcas `(red)` / `(green)` sin traducir. Scopes `inventario`, `pedidos`, `saga`, `gateway`, `contrato`, `infra`, `observabilidad`, `specs`, `adr`, `claude`, `docs`, `build`, `ci`.

## Consecuencias

**Positivas**
- Código, tests y mensajes uniformes en un solo idioma.
- El API HTTP, los estados del enunciado y el esquema se mantienen tal como los fija el enunciado.

**Negativas y riesgos**
- Conviven dos idiomas en las fronteras: DTO HTTP en español con `@JsonProperty` en inglés, entidades JPA con `@Column` en inglés y enums de dominio con constantes en inglés frente a enums de contrato en español. Mitigación: las reglas de `.claude/rules/java-style.md` lo documentan y los tests de contrato (`SobreMensajeJsonTest`, los `*ApiIT`) fijan el JSON de cada lado.
- Los nombres que se resuelven por reflexión (propiedades, JPQL, columnas por convención, `@MethodSource`, claves de configuración) no los valida el compilador. Mitigación: los tests de integración y de punta a punta los ejercitan.
- Las reglas de ArchUnit usan `allowEmptyShould(true)`: un paquete mal escrito dejaría una regla vacía sin fallar. Mitigación: las reglas evalúan los paquetes reales y se comprobó que seleccionan clases.
- Un mensaje publicado con otra versión del esquema no se puede reprocesar con este código. Si el contrato cambia, hay que subir `versionEsquema` o vaciar el outbox antes de desplegar.

## Cómo se verifica

- `./mvnw verify` (unitarios, ArchUnit e integración con Testcontainers) y `./mvnw verify -Pe2e` pasan.
- `SobreMensajeJsonTest.debe_usar_los_nombres_de_campo_del_contrato_al_serializar` fija los nombres JSON del sobre.
- `ProductoApiIT` y `PedidoApiIT` fijan los campos JSON del API HTTP; la colección de `postman/` y `load/oversell.js` los usan tal como los define el enunciado.
- `ArquitecturaHexagonalArchTest` en cada servicio evalúa los paquetes.

## Referencias

- [`docs/glosario.md`](../glosario.md)
- [0002](0002-kafka-como-sistema-de-mensajeria.md), [0004](0004-saga-orquestada-dentro-de-ms-pedidos.md), [0011](0011-idempotencia-http.md)
- Invariante 14 de `AGENTS.md` (migraciones inmutables)
