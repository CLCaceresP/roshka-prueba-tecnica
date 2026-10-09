---
paths:
  - "**/*.java"
---

# Estilo Java

- Java 25 (21 si alguna dependencia no lo soporta; ver ADR de versiones). Se usan `record`, `sealed`, pattern matching en `switch` y `var` cuando el tipo es evidente del lado derecho.
- El formato no se discute: lo aplica Spotless (hook automático). No se reformatea a mano.
- **Idioma.** Identificadores, comentarios, Javadoc, mensajes de log y de excepción en español, sin tildes ni `ñ` en los identificadores (`duenio`, `aplicacion`). Los textos para personas (mensajes, comentarios) sí llevan tildes. Se conservan en inglés los nombres de patrones y frameworks (`Outbox`, `Saga`, `Controller`, `Listener`, `Handler`, `Repository`, `Properties`, `Configuration`, `Filter`, `Entity`, `Request`, `Response`) y el contrato externo descrito en `AGENTS.md` (API HTTP, valores de estados, tablas y columnas). Vocabulario del dominio en `docs/glosario.md`.
- **Nombres del dominio.** Prohibidos `*ServiceImpl`, `*Impl`, `*Manager`, `*Gestor`, `*Helper`, `*Ayudante`, `*Util`, `*Utils`, `*Utilidades` y `*Procesador` genérico. Una clase de caso de uso se nombra por lo que hace: `ReservarStockHandler`, `CancelarPedido`, `CancelacionPedido`.
- **Interfaces** solo para puertos (`aplicacion/puertos/entrada`, `aplicacion/puertos/salida`). Una interfaz con una única implementación fuera de los puertos se elimina.
- **Records** para DTOs, comandos, eventos y value objects. Sin Lombok en `dominio/` ni en `aplicacion/`.
- **JSON del API HTTP.** Los componentes de los DTO de `adaptadores/entrada/rest` van en español y llevan `@JsonProperty` con el nombre en inglés del contrato (`@JsonProperty("orderId") UUID idPedido`). Los records del contrato de mensajes no llevan `@JsonProperty`: su JSON está en español.
- **Persistencia.** Las columnas y tablas siguen en inglés. Los campos de una entidad JPA llevan `@Column(name = ...)` explícito para no depender del nombre del campo Java.
- **Inmutabilidad por defecto.** Campos `final`; colecciones expuestas con `List.copyOf`. Los setters públicos en el dominio están prohibidos.
- **Errores.**
  - El dominio lanza excepciones propias y unchecked (`TransicionPedidoInvalida`, `ProductoInvalido`).
  - Los adaptadores REST las traducen a `ProblemDetail` (RFC 9457) con `type`, `title`, `status`, `detail` (en español) y la propiedad `code` (en inglés, estable; catálogo en la sección «Códigos de error» del README).
  - Nunca se captura `Exception` genérica salvo en el borde de un adaptador, y siempre se registra en el log.
- **Configuración.** Ningún timeout, backoff, umbral ni tamaño de lote en el código. Va en `application.yml`, con claves en español en kebab-case (`intervalo-sondeo`), y se lee con un `@ConfigurationProperties` tipado (record) cuyos componentes se llaman igual en camelCase.
- **Inyección** por constructor. Nunca `@Autowired` sobre campos.
- **Comentarios.** No se repite lo que dice el código. No hay banners, emojis ni `TODO` sin número de issue. Javadoc solo en puertos públicos y en decisiones no obvias (enlazando el ADR si existe).
- **Logs** en JSON estructurado. El contexto (`orderId`, `messageId`, `correlationId`) va en MDC o en pares clave/valor, no concatenado en el mensaje. Las claves del MDC y los nombres de métricas son contrato de observabilidad y se quedan en inglés.
- **Logs que se explican solos.** Un log nunca dice solo «procesado»: nombra el pedido o el producto, las cifras (cuánto pedía, cuánto había) y el motivo. Los procesos usan `Pasos` (un log por paso con `durationMs` y un total); las consultas dicen qué devolvieron y los errores HTTP, su `code` y su detalle. Ver la spec 010.
- **Sin código muerto.** Nada de dependencias sin usar, endpoints de ejemplo ni configuración copiada que no se usa.
