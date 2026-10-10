# 0019 · Logs por pasos con tiempos mediante un cronometraje explícito

- Estado: Aceptado
- Fecha: 2026-10-10
- Requisitos: BON-03, REQ-18

## Contexto y problema

Con la [spec 008](../../specs/008-trazabilidad-de-logs/spec.md) los logs ya llegan a Seq con enrichers, pero solo hay una línea al terminar cada solicitud y algunos eventos sueltos. Para seguir un pedido hace falta ver el request cuando entra, cada paso del proceso con lo que tardó, el código HTTP y el tiempo total. Los mensajes son frases fijas, así que el método, el estado y los milisegundos solo se ven abriendo cada evento.

## Factores de decisión

- Que un proceso se lea de arriba abajo en Seq, con la duración de cada paso.
- Que la capa `aplicacion` siga sin depender de frameworks de infraestructura (regla de ArchUnit): SLF4J sí es admisible, Micrometer y Spring Web no.
- Que no cambie el comportamiento: los tests de negocio existentes siguen en verde.
- Que cada paso quede visible en el código del caso de uso, sin magia.
- Que el MDC y los atributos no repitan claves (la consola JSON falla con claves duplicadas).

## Opciones consideradas

1. **Una clase `Pasos` con cronometraje explícito (elegida).** El caso de uso envuelve cada paso con `pasos.medir("...", () -> ...)`. Es código plano, depende solo de SLF4J y deja ver en el propio caso de uso qué pasos tiene.
2. **Aspectos de Spring AOP sobre los puertos.** Registraría las llamadas a repositorios y outbox sin tocar los casos de uso, pero agrega la dependencia de AOP, los nombres de los pasos quedarían en anotaciones o convenciones de método y no se pueden describir en lenguaje de dominio ("Pedido guardado en la base de datos").
3. **Observaciones de Micrometer (`@Observed` o `Observation`) con un handler que registre el log.** Reutiliza las trazas, pero la capa `aplicacion` no puede depender de Micrometer y exigiría un puerto de salida solo para cronometrar.
4. **Cronometrar solo en los adaptadores (repositorios, outbox).** No toca los casos de uso, pero el tiempo queda atado a cada adaptador y no se ve el proceso completo ni el orden de los pasos de negocio.

## Decisión

Opción 1.

- `Pasos` vive en `aplicacion` de cada servicio (como `AtributosLog`), con SLF4J y sin Spring. Cada paso escribe `<paso> (<n> ms)` con `process`, `step`, `durationMs` y `outcome`; un paso que falla escribe en WARN y propaga el error; el proceso escribe `<proceso> completado en <n> ms (<k> pasos)`.
- El texto de cada mensaje incluye los datos clave (método, ruta, estado, milisegundos) y los mismos datos van como atributos para filtrar.
- `Pasos` omite las claves que el MDC ya aporta (`orderId`, `correlationId`, `messageId`) para no duplicarlas en la consola JSON.
- Cada evento de Kafka se registra al recibirse, al procesarse y al finalizar, y sus detalles (`eventType`, `eventTopic`, `eventPartition`, `eventOffset`, `causationId`) se dejan en el MDC mientras dura su procesamiento, para que cada paso los lleve sin repetirlos.
- Las solicitudes HTTP registran la entrada (`Solicitud HTTP recibida`) y la salida con el estado y el tiempo total.
- El cronómetro de un paso no incluye el `commit` de la transacción que lo envuelve; la solicitud idempotente sí lo incluye porque su transacción se abre y se cierra dentro del paso.
- Los ids del pedido y del producto los genera el servidor; los requests no los aceptan.

## Consecuencias

**Positivas**
- Un pedido se lee en Seq como una secuencia de pasos con sus milisegundos y el código HTTP final.
- Cada caso de uso muestra sus pasos en el código; no hay comportamiento escondido.

**Negativas y riesgos**
- Más volumen de logs (decenas de líneas por pedido). Mitigación: las sondas siguen en DEBUG y los pasos son INFO, así que se pueden bajar con `logging.level.<paquete>`.
- Los casos de uso quedan algo más largos al envolver cada paso en una lambda. Mitigación: la clase es pequeña y los nombres de los pasos documentan el flujo.
- Una lambda por paso agrega un costo mínimo de CPU por llamada.
- El tiempo de un paso medido dentro de una transacción no incluye el commit.
- `Pasos` queda duplicada en los dos servicios (no hay módulo compartido para lógica; `contrato-eventos` no puede tenerla).

## Cómo se verifica

- Prueba unitaria de `Pasos` (paso correcto, paso que falla, atributos y claves del MDC).
- ITs `LogsPasosIT` en cada servicio con un exportador de logs en memoria, y verificación en Seq (010-AC-08).

## Referencias

- [Spec 010](../../specs/010-logs-paso-a-paso/spec.md) · [ADR 0017](0017-logs-de-solicitudes-y-enrichers.md)
