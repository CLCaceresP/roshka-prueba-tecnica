# AGENTS.md

Fuente única de verdad para cualquiera que trabaje en este repositorio. Si algo de aquí contradice el enunciado (`docs/enunciado/`), gana el enunciado y se corrige este archivo.

## Propósito

Sistema distribuido de pedidos e inventario que reserva stock **sin vender más unidades de las disponibles**, tolerando solicitudes concurrentes, múltiples instancias, mensajes duplicados (at-least-once), errores temporales y la carrera entre cancelación y reserva.

- Enunciado: `docs/enunciado/prueba-tecnica.pdf`
- Requisitos numerados: `docs/enunciado/requisitos.md` (`REQ-xx`, `BON-xx`)
- Decisiones: `docs/adr/`
- Vocabulario del dominio: `docs/glosario.md`
- Specs (SDD): `specs/NNN-<feature>/{spec,plan,tasks}.md`

## Mapa de módulos

| Módulo | Responsabilidad | Estado |
|---|---|---|
| `contrato-eventos` | Records inmutables de comandos/eventos y el sobre (`SobreMensaje`). Sin lógica. | Fase 1 |
| `ms-inventario` | Dueño de `Producto`, `Reserva` y del stock. Consume `inventario.comandos`, publica `inventario.eventos`. BD `inventory_db`. | Fase 2 |
| `ms-pedidos` | Dueño del agregado `Pedido` y del orquestador de la saga (paquete `saga`). Publica `inventario.comandos`, consume `inventario.eventos`. BD `orders_db`. | Fase 3 |
| `gateway-pedidos-inventario` | Spring Cloud Gateway delgado: ruteo, `X-Correlation-Id`, presencia de `Idempotency-Key`, rate limiting, propagación de trazas. Sin lógica de negocio. | Fase 5 |
| `infra/` | Configuración de Postgres, Prometheus, Grafana y OTel Collector montada en `docker-compose`. Los logs van por OTLP al collector y de ahí a Seq (ADR 0016). | Fase 1 |
| `postman/` | Colección de integración de Postman con aserciones y su entorno; se ejecuta con Newman. | Fase 6 |
| `front/` | Cliente local HTML/CSS/JS y proxy Node.js sin dependencias para probar el gateway; `scripts/front.sh` y `scripts/front.ps1`. | Herramienta local |
| `load/` | Scripts k6 de carga concurrente. | Fase 6 |
| `scripts/` | Scripts para el evaluador, en `.sh` y `.ps1`. | Fase 1 |
| `pruebas-sistema` | Tests end-to-end contra el stack de `docker-compose` (perfil Maven `e2e`). | Fase 4 |

Estructura interna de cada servicio (hexagonal, verificada con ArchUnit):

```
dominio/               modelo puro: sin Spring, sin JPA, sin Kafka. Invariantes y máquina de estados.
aplicacion/            casos de uso (una clase por caso de uso), puertos de entrada y de salida.
adaptadores/entrada/   REST controllers, Kafka listeners, jobs.
adaptadores/salida/    JPA, JDBC, outbox, Redisson, métricas, clientes.
configuracion/         wiring de Spring.
```

## Comandos

Se ejecutan desde la raíz del repo, en Git Bash. En PowerShell, `./mvnw` → `.\mvnw.cmd` y `scripts/*.sh` → `scripts/*.ps1`. Disponibles desde la fase indicada.

| Acción | Comando | Desde |
|---|---|---|
| Build completo + todos los tests | `./mvnw verify` | Fase 1 |
| Tests unitarios de un módulo | `./mvnw -pl ms-inventario test` | Fase 1 |
| Un test concreto | `./mvnw -pl ms-inventario -am test -Dtest=ReservarStockHandlerTest -Dsurefire.failIfNoSpecifiedTests=false` | Fase 2 |
| Integración de un módulo (Testcontainers, `*IT`) | `./mvnw -pl ms-inventario verify` | Fase 2 |
| Suite concurrente (Testcontainers) | `./mvnw verify -Dgroups=concurrency` | Fase 4 |
| End-to-end contra el stack de compose (puertos libres) | `./mvnw verify -Pe2e` | Fase 4 |
| Formatear | `./mvnw spotless:apply` | Fase 1 |
| Verificar formato | `./mvnw spotless:check` | Fase 1 |
| Levantar todo | `scripts/up.sh` | Fase 1 |
| Bajar todo | `scripts/down.sh` | Fase 1 |
| Tests | `scripts/test.sh` | Fase 1 |
| Colección Postman | `scripts/postman.sh` (usa `npx newman`; requiere Node.js) | Fase 6 |
| Carga k6 | `scripts/k6.sh` (requiere `k6`: `winget install k6 --source winget`) | Fase 6 |

Testcontainers necesita Docker Desktop corriendo con backend WSL2.

## Invariantes del sistema

No son negociables. Un cambio que rompa una invariante se rechaza aunque los tests pasen.

1. **Stock nunca negativo.** Para todo producto, `available >= 0` y `reserved >= 0`. Lo garantiza la base de datos (`CHECK` y `UPDATE` condicional `WHERE available >= :qty`), no un lock.
2. **Conservación del stock.** Para todo producto, `available + reserved = total`, donde `total` es el stock cargado, incluidos los cambios hechos con `PUT /products/{id}/stock` (que fija `available` y deja `reserved` intacto), con un tope de 10.000.000 impuesto también por un `CHECK`.
3. **Reserva atómica.** Un pedido reserva todos sus ítems o ninguno, en una sola transacción. Las filas de productos se actualizan en orden ascendente de `idProducto` (columna `product_id`).
4. **Una reserva por pedido.** En `ms-inventario` existe como máximo una fila de `reservations` por `order_id`. Transiciones válidas: `RESERVED → RELEASED`. `RELEASED` y `REJECTED` son terminales. Un `RELEASED` sin reserva previa es un tombstone y bloquea cualquier `ReservarStock` posterior.
5. **El stock de una reserva se libera como máximo una vez.**
6. **Máquina de estados de `Pedido`.** `PENDING → CONFIRMED | REJECTED | CANCELLED`; `CONFIRMED → CANCELLED`. `REJECTED` y `CANCELLED` son terminales. Una transición inválida es un error de dominio. Un evento tardío nunca saca a un pedido de un estado terminal.
7. **Todo mensaje se publica vía outbox**, en la misma transacción que el cambio de negocio que lo origina. El código de negocio nunca llama a `KafkaTemplate` directamente.
8. **Todo consumidor es idempotente.** El registro del `idMensaje` en `processed_messages` y el efecto de negocio ocurren en la misma transacción.
9. **Key de partición = `idAgregado` (`idPedido`)** en todo mensaje.
10. **El offset de Kafka se confirma solo después del commit de la transacción de base de datos.**
11. **Una base de datos por servicio.** Ningún servicio lee ni escribe tablas de otro. `contrato-eventos` no contiene lógica.
12. **Idempotencia HTTP.** Todo endpoint de escritura exige `Idempotency-Key`: misma key + mismo body → misma respuesta almacenada; misma key + body distinto → `422`; key en proceso → `409` con `Retry-After`.
13. **Los locks distribuidos son defensa en profundidad.** La corrección nunca depende solo de un lock. Todo lock tiene timeout de espera y lease explícitos; nunca se bloquea indefinidamente. El publisher renueva su lock antes de cada envío y detiene el lote si falla la renovación. Su lease debe cubrir `max.block.ms`, `timeout-envio` y el marcado en la base (ADR 0014).
14. **Las migraciones de Flyway ya commiteadas son inmutables.** Un cambio de esquema es una migración nueva.

## Convenciones

- **Idiomas.** Todo en español: identificadores (clases, métodos, variables, paquetes), tests, comentarios, Javadoc, mensajes de log y de excepción, `title` y `detail` de los `ProblemDetail`, mensajes de commit, README, specs, ADRs y configuración de agentes. Sin tildes ni `ñ` en identificadores, paquetes ni nombres de archivo (`aplicacion`, `anio`). Los términos de infraestructura y patrones universales se conservan: `Outbox`, `Saga`, `DLT`, `Controller`, `Listener`, `Handler`, `Repository`, `Properties`, `Configuration`, `Filter`, `Entity`, `Request`, `Response`, `Job`, `Application`, y los nombres de frameworks (`Kafka`, `Jdbc`, `Jpa`, `Redis`).
- **Lo que se queda en inglés** por ser contrato externo o dato persistido, no identificador: paths, headers y campos JSON del API HTTP (`/orders`, `Idempotency-Key`, `orderId`); estados y valores del enunciado (`PENDING`, `CONFIRMED`, `REJECTED`, `CANCELLED`) y los demás valores de enums que se persisten o salen por HTTP; el campo `code` de los errores (`INSUFFICIENT_STOCK`); tablas, columnas y nombres de base de datos; nombres de métricas. Las migraciones de Flyway ya commiteadas no se tocan (invariante 14). Los tópicos de Kafka y el JSON de los eventos sí están en español.
- **Commits.** Conventional Commits en español: el tipo (`feat`, `fix`, `test`, `refactor`, `docs`, `build`, `ci`) y las marcas `(red)` / `(green)` se escriben tal cual; la descripción va en español. Pequeños, una preocupación por commit. Scopes: `inventario`, `pedidos`, `saga`, `gateway`, `contrato`, `infra`, `observabilidad`, `specs`, `adr`, `claude`, `docs`, `build`, `ci`.
- **Ciclo TDD visible en el historial:** `test(<scope>): ... (red)` → `feat(<scope>): ... (green)` → `refactor(<scope>): ...` opcional.
- **Nombres del dominio.** Nada de `*ServiceImpl`, `*Impl`, `*Manager`, `*Gestor`, `*Helper`, `*Ayudante`, `*Util` ni `*Utilidades`. Una clase se nombra por lo que hace, con vocabulario del dominio en español (`docs/glosario.md`). Ejemplos válidos: `ReservarStockHandler`, `CancelacionPedido`, `LibroStock`.
- **Interfaces** solo para los puertos hexagonales.
- **Records** para DTOs y mensajes. Sin Lombok en el dominio.
- **Errores HTTP** con `ProblemDetail` (RFC 9457) y un código de error propio documentado.
- **Configuración.** Todo timeout, backoff y umbral vive en `application.yml` con un nombre claro. Sin números mágicos.
- **Comentarios.** Sin comentarios que repitan el código, banners, emojis ni `TODO` sin issue. Javadoc solo en puertos y en decisiones no obvias.
- **Versiones de dependencias.** Se verifican contra la fuente oficial antes de fijarlas; nunca se escriben de memoria. Java 25 LTS (21 solo si una dependencia no lo soporta), la última versión estable de Spring Boot, y Spring Cloud, Testcontainers, Redisson y ShedLock compatibles con ella. Se registran en el ADR de plataforma.
- **Persistencia.** Flyway para el esquema. Nunca `ddl-auto`. Toda invariante expresable en SQL se expresa como constraint.
- **Tests.** Nombres `debe_<resultado>_cuando_<condición>`, sin tildes. Sin `Thread.sleep`: Awaitility para la consistencia eventual, `ExecutorService` + `CountDownLatch` para la concurrencia. Integración con Testcontainers (Postgres, Kafka y Redis reales); nada de H2 ni Kafka embebido.
- **Logs que se explican solos.** Todo log dice qué pasó, a qué pedido o producto, con qué cifras y por qué (quién lo pidió, cuánto había, qué motivo). Los procesos de negocio se escriben con `Pasos` (un log por paso con su duración y un total); cada endpoint registra la solicitud al entrar y al salir, las consultas dicen qué devolvieron y cada error HTTP dice su código y su detalle. Nunca se registran cuerpos completos ni secretos (spec 010).
- **Trazabilidad.** Cada spec y cada test de aceptación cita los `REQ-xx` que cubre.

## Definición de "terminado"

Una tarea está terminada cuando:

1. Existe una spec en `specs/` con criterios Given/When/Then que citan sus `REQ-xx`.
2. El historial muestra el test rojo commiteado antes que la implementación.
3. `./mvnw verify` pasa en limpio: tests unitarios, de integración y ArchUnit, y `spotless:check`.
4. Ninguna invariante de esta lista se ve afectada, o el cambio la verifica con un test.
5. Toda decisión con alternativas razonables tiene su ADR.
6. Los revisores (`idempotency-reviewer`, `architecture-reviewer`, `test-reviewer`) no dejan hallazgos bloqueantes sin resolver.
7. Este archivo, el README y la matriz de trazabilidad reflejan el cambio si corresponde.
