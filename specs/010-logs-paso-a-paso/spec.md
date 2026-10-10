# 010 · Logs paso a paso con tiempos

Estado: Aprobada
Requisitos: BON-03, REQ-18, REQ-22, REQ-19

## Contexto

La [spec 008](../008-trazabilidad-de-logs/spec.md) agregó una línea por solicitud y algunos eventos de negocio, pero en Seq un pedido sigue siendo difícil de leer: no se ve el request cuando **entra**, no se ve **qué paso** se hizo ni **cuánto tardó cada uno**, y los mensajes son frases fijas sin datos (hay que abrir cada evento para ver el método, el estado o los milisegundos). Esta spec hace que cada proceso se pueda leer de arriba abajo: request recibido, pasos con su duración, resultado con su código HTTP y el tiempo total.

Decisiones en el [ADR 0019](../../docs/adr/0019-logs-por-pasos-con-tiempos.md).

## Reglas de negocio

- **RN-01** Al **entrar** una solicitud HTTP (gateway y servicios) se escribe `Solicitud HTTP recibida: <MÉTODO> <ruta>` con `method`, `path` (ruta real, sin plantilla), `query`, `clientIp`, `host`, `userAgent`, `contentLength` y `correlationId`. Al **salir**, `Solicitud HTTP procesada: <MÉTODO> <plantilla> respondió <estado> en <n> ms` con `status` y `durationMs`. Las sondas siguen en DEBUG.
- **RN-02** Todo mensaje de log es **autoexplicativo**: su **texto** incluye los identificadores y los datos que lo explican (pedido, producto, cantidades, stock, motivo, método, ruta, estado, milisegundos), no solo un título. Esos mismos datos van como atributos filtrables, con nombres en inglés (`orderId`, `productId`, `requested`, `available`, `step`, `process`, `durationMs`, `outcome`).
- **RN-02b** Los eventos de negocio dicen quién y cuánto:
  - `Pedido <orderId> colocado: <n> líneas, <u> unidades (<productId>×<cantidad>, …)`.
  - `Reserva del pedido <orderId> aprobada (<n> líneas, <u> unidades)`.
  - `Reserva del pedido <orderId> rechazada por stock insuficiente: producto <productId> pedía <q> y había <a> disponibles` (una entrada por producto que no alcanzó); con producto inexistente o pedido ya liberado, el motivo correspondiente.
  - `Pedido <orderId> rechazado: <motivo legible> (pedía <productId>×<cantidad>, …)`; con `TIMEOUT`, cuánto esperó.
  - `Pedido <orderId> confirmado en <n> ms (saga completa)`, `Pedido <orderId> cancelado (estaba <estado>)`.
  - Las solicitudes que crean un recurso incluyen su `Location` (el id generado) en la línea de salida.
  - Los eventos de Kafka incluyen un resumen de su contenido (líneas, motivo, desenlace).
- **RN-02c** **Regla general: ningún log puede ser ambiguo.** Aplica a todos los endpoints de todos los servicios y a todo lo que corre en segundo plano. Un log debe poder leerse solo y responder qué hizo, sobre qué (ids), con qué datos y con qué resultado. En particular:
  - **Cada endpoint dice qué recibió y qué devolvió**:
    - `POST /orders`: `Pedido <id> colocado: …` (ver RN-02b).
    - `GET /orders/{orderId}`: `Pedido <id> consultado: estado <estado>, <n> líneas, <u> unidades (<productId>×<q>, …)` y, si fue rechazado, su motivo.
    - `DELETE /orders/{orderId}`: `Pedido <id> cancelado …` o `Pedido <id> ya estaba <estado>: no cambia nada`.
    - `POST /products`: `Producto <id> registrado: "<nombre>" con <n> unidades disponibles`.
    - `GET /products/{productId}/stock`: `Producto <id>: <a> disponibles, <r> reservados, <t> en total`.
    - Rutas del gateway: la línea de entrada y la de salida, con la ruta elegida y el destino.
  - **Cada error HTTP dice por qué**: `Respuesta de error <estado> <code>: <detalle>` (por ejemplo `404 ORDER_NOT_FOUND: El pedido <id> no existe`, `400 VALIDATION_FAILED: …`, `422 IDEMPOTENCY_KEY_REUSED`, `409 IDEMPOTENCY_KEY_IN_PROGRESS`, `503 LOCK_UNAVAILABLE`), en WARN para 4xx y ERROR para 5xx. El gateway explica igual sus rechazos propios (`400` por falta de `Idempotency-Key`, `429` por exceso de tasa).
  - **Los trabajos en segundo plano dicen qué revisaron y qué hicieron**: `Revisión de sagas estancadas: <n> pedidos expirados en <ms> ms` (INFO si expiró alguno, DEBUG si no), el publisher del outbox `Outbox: <n> filas publicadas en <ms> ms` o `Outbox: fila <id> sin publicar (<motivo>); se reintenta en el próximo ciclo`, y los reintentos o el envío a la DLT con la causa.
  - Los mensajes de infraestructura propios (arranque, bloqueos, salud) también nombran el recurso afectado.
- **RN-03** Un **proceso** (por ejemplo, colocar un pedido) se compone de **pasos**. Cada paso, al terminar, escribe `<paso> (<n> ms)` con `process`, `step`, `durationMs` y `outcome=ok`. Si falla, escribe en WARN `<paso> falló tras <n> ms: <motivo>` con `outcome=error` y vuelve a lanzar el error.
- **RN-04** Al terminar un proceso se escribe `<proceso> completado en <n> ms (<k> pasos)`; si falla, en WARN `<proceso> falló tras <n> ms`.
- **RN-05** Procesos y pasos registrados:
  - **Colocar pedido** (`ms-pedidos`): pedido creado y validado, pedido guardado en la base de datos, comando `ReservarStock` registrado en el outbox.
  - **Cancelar pedido** (`ms-pedidos`): pedido cargado, cancelación aplicada, pedido actualizado y comandos registrados.
  - **Solicitud idempotente** (ambos): clave de idempotencia consultada, y o bien `Solicitud repetida: se reprodujo la respuesta almacenada` o bien solicitud ejecutada y respuesta registrada en la misma transacción.
  - **Aplicar respuesta de inventario** (`ms-pedidos`): mensaje marcado como procesado, pedido cargado, transición aplicada, pedido actualizado y comandos registrados.
  - **Expirar pedido** (`ms-pedidos`): pedido cargado, expiración aplicada, pedido actualizado.
  - **Registrar producto** (`ms-inventario`): producto creado y validado, producto guardado en la base de datos.
  - **Consultar stock** (`ms-inventario`): stock consultado en la base de datos.
  - **Reservar stock** (`ms-inventario`): mensaje marcado como procesado, reserva consultada, stock reservado en la base de datos (si es nueva), reserva guardada, respuesta registrada en el outbox.
  - **Liberar stock** (`ms-inventario`): mensaje marcado como procesado, reserva consultada, stock devuelto o tombstone registrado, respuesta registrada en el outbox.
- **RN-06** Los bloqueos distribuidos registran `Bloqueo <nombre> adquirido en <n> ms` y `Bloqueo <nombre> liberado tras retenerlo <n> ms`.
- **RN-07** Cada **evento** (mensaje de Kafka, sea comando o respuesta) se registra en tres momentos, siempre con sus detalles:
  - **Recibido**: `Evento recibido: <tipo> (<tópico> partición <p>, offset <o>)`, apenas llega al listener.
  - **Procesando**: `Procesando evento: <tipo> del pedido <orderId>`, justo antes de ejecutar el caso de uso.
  - **Finalizado**: `Evento finalizado: <tipo> → <resultado> en <n> ms`; si falla, en WARN `Evento falló: <tipo> tras <n> ms: <motivo>`.
  Los detalles del evento viven en el MDC mientras dura su procesamiento, así que **todo log de los pasos** (RN-05) los trae también: `eventType`, `eventTopic`, `eventPartition`, `eventOffset`, `messageId`, `causationId`, `orderId` y `correlationId`. Un mensaje enviado a la DLT registra tópico, partición, offset y causa. La publicación del outbox registra el evento emitido: `Evento publicado: <tipo> en <tópico> (<n> ms)`, con los mismos detalles y el tiempo de la confirmación del broker.
- **RN-08** Al llegar un pedido a un estado final se registra, además, el tiempo de toda la saga: `sagaDurationMs`, desde que se colocó.
- **RN-09** El `id` del pedido y el del producto **los genera siempre el servidor** (UUID aleatorio). El cliente no los envía; si los incluye en el cuerpo, la API los rechaza con `400` ([spec 011](../011-validaciones-de-producto-y-contrato-http/spec.md), RN-04). Ambos aparecen en los logs como `orderId` y `productId`.

## Criterios de aceptación

### 010-AC-01 · La solicitud se registra al entrar y al salir (BON-03)
- Given un servicio con exportador de logs
- When recibe `GET /orders/{id}` con `X-Correlation-Id`
- Then el exportador recibe `Solicitud HTTP recibida: GET /orders/<id>` y después `Solicitud HTTP procesada: GET /orders/{orderId} respondió 200 en <n> ms`, ambas con el mismo `correlationId`, y la primera es anterior a la segunda

### 010-AC-02 · Colocar un pedido se lee paso a paso con sus tiempos (BON-03, REQ-19)
- Given `ms-pedidos` con exportador de logs
- When llega `POST /orders` con `Idempotency-Key` y `X-Correlation-Id`
- Then, ordenados por tiempo y filtrados por `correlationId`, aparecen: solicitud recibida, clave de idempotencia consultada, pedido creado y validado, pedido guardado en la base de datos, comando `ReservarStock` registrado en el outbox, `Colocar pedido completado en <n> ms`, y solicitud procesada con `respondió 202`; todos los pasos con `durationMs`

### 010-AC-03 · Registrar un producto y reservar stock se leen paso a paso (BON-03, REQ-22)
- Given `ms-inventario` con exportador de logs
- When llega `POST /products`
- Then aparecen `Producto creado y validado`, `Producto guardado en la base de datos` y `Registrar producto completado en <n> ms`
- Given un producto con stock
- When llega un `ReservarStock` por Kafka
- Then aparecen, con `orderId` y `durationMs`: mensaje marcado como procesado, reserva consultada, stock reservado en la base de datos, reserva guardada, respuesta registrada en el outbox y `Reservar stock completado en <n> ms`

### 010-AC-04 · Una solicitud repetida y un paso que falla se distinguen (BON-03)
- Given un `POST /orders` ya procesado
- When se repite con la misma `Idempotency-Key`
- Then aparece `Solicitud repetida: se reprodujo la respuesta almacenada` y no aparece `Pedido guardado en la base de datos`
- Given un paso que falla
- When se lanza un error dentro del paso
- Then se registra en WARN `<paso> falló tras <n> ms` con `outcome=error` y el error se propaga

### 010-AC-05 · Cada evento se registra al recibirse, al procesarse y al finalizar (BON-03)
- Given un evento de Kafka que llega a un servicio
- When se procesa
- Then, para ese `messageId`, aparecen en orden `Evento recibido: <tipo> (<tópico> partición <p>, offset <o>)`, `Procesando evento: <tipo> del pedido <orderId>`, los pasos del proceso y `Evento finalizado: <tipo> → APLICADO en <n> ms`
- And todos esos logs, incluidos los pasos, traen `eventType`, `eventTopic`, `eventPartition`, `eventOffset`, `messageId`, `orderId` y `correlationId`
- Given un evento cuyo procesamiento falla
- Then se registra en WARN `Evento falló: <tipo> tras <n> ms: <motivo>`
- Given una fila del outbox publicada
- Then el log dice `Evento publicado: <tipo> en <tópico> (<n> ms)` con `durationMs` y los detalles del evento
- Given un evento enviado a la DLT
- Then el log trae tópico, partición, offset y causa
- Given una operación con bloqueo
- Then aparecen `Bloqueo <nombre> adquirido en <n> ms` y `Bloqueo <nombre> liberado tras retenerlo <n> ms`

### 010-AC-06 · La saga informa su duración total (BON-03)
- Given un pedido colocado
- When llega `StockReservado`
- Then `Pedido confirmado` trae `sagaDurationMs`

### 010-AC-11 · Las consultas dicen qué devolvieron (BON-03, REQ-20, REQ-23)
- Given un pedido existente
- When llega `GET /orders/{orderId}`
- Then se registra `Pedido <id> consultado: estado <estado>, <n> líneas, <u> unidades (<productId>×<q>, …)` con `orderId`, `status`, `lines` y `units`
- Given un producto existente
- When llega `GET /products/{productId}/stock`
- Then se registra `Producto <id>: <a> disponibles, <r> reservados, <t> en total`

### 010-AC-12 · Cada error HTTP dice por qué (BON-03, REQ-24)
- Given un pedido inexistente
- When llega `GET /orders/{orderId}`
- Then se registra en WARN `Respuesta de error 404 ORDER_NOT_FOUND: El pedido <id> no existe` con `status` y `code`
- Given un `POST /orders` sin líneas
- Then se registra en WARN `Respuesta de error 400 VALIDATION_FAILED: <detalle>`
- Given un `POST` con una `Idempotency-Key` reutilizada con otro cuerpo
- Then se registra en WARN `Respuesta de error 422 IDEMPOTENCY_KEY_REUSED: …`
- Given una escritura sin `Idempotency-Key` en el gateway
- Then el gateway registra `Solicitud rechazada en el gateway: 400 IDEMPOTENCY_KEY_MISSING …`

### 010-AC-13 · Los trabajos en segundo plano dicen qué hicieron (BON-03)
- Given pedidos pendientes que superaron el umbral
- When corre el job de timeout
- Then se registra `Revisión de sagas estancadas: <n> pedidos expirados en <ms> ms`
- Given una fila del outbox que no se pudo publicar
- Then se registra `Outbox: fila <id> sin publicar (<motivo>); se reintenta en el próximo ciclo`

### 010-AC-07 · Los ids los genera el servidor (REQ-22, REQ-19)
- Given dos `POST /products` idénticos
- Then los `productId` de las respuestas son distintos
- Given un `POST /products` cuyo cuerpo incluye un `productId` propio
- Then responde `400 VALIDATION_FAILED` y no se crea el producto con ese id
- Given un `POST /orders` cuyo cuerpo incluye un `orderId` propio
- Then responde `400 VALIDATION_FAILED`; el id de un pedido válido siempre es uno nuevo generado por el servidor

### 010-AC-09 · Un rechazo se explica solo (BON-03, REQ-11)
- Given un producto con 3 unidades disponibles
- When llega un `ReservarStock` de 5 unidades de ese producto
- Then `ms-inventario` registra `Reserva del pedido <orderId> rechazada por stock insuficiente: producto <productId> pedía 5 y había 3 disponibles`, con `orderId`, `productId`, `requested=5` y `available=3`
- And `ms-pedidos`, al llegar `StockRechazado`, registra `Pedido <orderId> rechazado: stock insuficiente (pedía <productId>×5)`

### 010-AC-10 · Colocar un pedido dice qué se pidió (BON-03, REQ-19)
- Given `ms-pedidos`
- When se coloca un pedido de 2 productos
- Then el log dice `Pedido <orderId> colocado: 2 líneas, <u> unidades (<productId>×<q>, <productId>×<q>)` y la línea de salida de `POST /orders` incluye el `Location` con ese id

### 010-AC-08 · El recorrido completo se lee en Seq (BON-03, REQ-18)
- Given el stack levantado
- When se crea un pedido por el gateway con `X-Correlation-Id`
- Then, filtrando por ese id y ordenando por tiempo, se lee: request del gateway, request del servicio, pasos de colocar con sus ms, publicación en Kafka, pasos de reservar en inventario, publicación de la respuesta, pasos de aplicar la respuesta, `Pedido confirmado` con `sagaDurationMs` y las dos líneas de solicitud procesada con su código HTTP

## Casos borde
- El tiempo de un paso no incluye el `commit` de la transacción que lo envuelve; el `commit` queda dentro de `Solicitud idempotente` (que sí lo incluye) y del tiempo total de la solicitud.
- Los pasos de un proceso que corre dentro de un listener comparten el `orderId` y el `correlationId` del MDC; no se repiten como atributos (la consola JSON falla con claves duplicadas).
- No se registran cuerpos de solicitudes ni de respuestas.

## Fuera de alcance
- Métricas nuevas (los tiempos van en logs; las métricas existentes no cambian).
- Registrar el SQL ni los tiempos del driver.
- Reemplazar las trazas de Jaeger.

## Preguntas abiertas
Ninguna.
