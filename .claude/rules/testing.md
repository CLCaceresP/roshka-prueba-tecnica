---
paths:
  - "**/src/test/**"
  - "specs/**"
---

# Testing

## TDD estricto

1. No se escribe código de producción sin un test que falle primero.
2. El test rojo se ejecuta y se confirma que falla **por la razón correcta**: una aserción de comportamiento, no un error de compilación ni de configuración. Un test que no compila no cuenta como rojo; antes se crean los tipos mínimos vacíos.
3. Commit `test(<scope>): <comportamiento> (red)`.
4. Implementación mínima, commit `feat(<scope>): <comportamiento> (green)`.
5. Refactor opcional en un commit aparte, con los tests en verde.

La descripción del commit va en español; las marcas `(red)` y `(green)` se escriben tal cual para que `git log` las encuentre.

## Nombres y estructura

- Formato obligatorio: `debe_<resultado>_cuando_<condicion>`, sin tildes ni `ñ`. Ejemplo: `debe_rechazar_la_reserva_cuando_el_stock_es_insuficiente`.
- Given / When / Then visibles: bloques separados por una línea en blanco. No hacen falta comentarios.
- Los tests de aceptación citan su requisito y su criterio con `@Tag` o `@DisplayName` (en español), por ejemplo `@DisplayName("REQ-13 · 001-AC-04 ...")`.
- Un test verifica un comportamiento. Varias aserciones están permitidas si describen el mismo resultado.
- Las clases de test llevan el nombre en español más el sufijo del nivel (`PedidoTest`, `InventarioConcurrenciaIT`).

## Niveles

| Nivel | Sufijo | Herramientas | Sin |
|---|---|---|---|
| Dominio y casos de uso | `*Test` | JUnit 5, AssertJ, fakes en memoria de los puertos | Spring |
| Arquitectura | `*ArchTest` | ArchUnit | — |
| Integración | `*IT` | Testcontainers con Postgres, Kafka y Redis reales | H2, Kafka embebido |
| Concurrencia | `*ConcurrenciaIT`, `@Tag("concurrency")` | `ExecutorService`, `CountDownLatch`, Awaitility | `Thread.sleep` |

## Prohibido

- `Thread.sleep`, `TimeUnit.*.sleep` y esperas activas. Para la consistencia eventual se usa Awaitility con un timeout explícito.
- Mocks de lo que se puede usar real. En los casos de uso se prefieren fakes de los puertos; Mockito solo para verificar interacciones con un puerto de salida cuando no hay estado observable.
- Aserciones débiles: `isNotNull()` como única aserción, `assertTrue(list.size() > 0)` o capturar excepciones sin verificar el tipo y el mensaje.
- Tests que dependen del orden de ejecución o de datos de otro test. Cada test crea sus productos y pedidos con IDs únicos.
- `@Disabled` sin un issue enlazado.

## Pruebas concurrentes

- Arranque simultáneo: los hilos esperan en un `CountDownLatch` y se liberan juntos.
- Las aserciones van sobre el **estado final**: conteos por estado, stock `available` y `reserved` (columnas), `available >= 0` y `available + reserved = total`.
- El número de hilos y de iteraciones se fija como constante con nombre dentro del test.
