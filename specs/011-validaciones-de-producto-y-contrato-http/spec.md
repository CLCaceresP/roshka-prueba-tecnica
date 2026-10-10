# 011 · Validaciones de producto y contrato HTTP estricto

Estado: Aprobada
Requisitos: REQ-22, REQ-10, REQ-24, REQ-19

## Contexto

Hoy `POST /products` permite crear el mismo producto (mismo nombre) todas las veces que se quiera, y los cuerpos HTTP con campos que el contrato no define se aceptan en silencio (por ejemplo un `productId` propio al crear un producto o un `orderId` al crear un pedido). Esta spec agrega las validaciones que faltaban. Las decisiones están en el [ADR 0020](../../docs/adr/0020-unicidad-del-nombre-y-contrato-http-estricto.md).

## Reglas de negocio

- **RN-01 · Nombre normalizado.** Antes de validar y guardar, el nombre del producto se normaliza: se recortan los espacios de los extremos y los espacios internos repetidos (incluidos tabuladores y saltos de línea) se reducen a uno. Los caracteres de control se rechazan. La longitud (1 a 120) se mide sobre el nombre ya normalizado, y la respuesta devuelve el nombre normalizado.
- **RN-02 · Nombre único.** No pueden existir dos productos cuyo nombre normalizado sea igual sin distinguir mayúsculas ni minúsculas (`Teclado`, ` teclado ` y `TECLADO` son el mismo). Crear un duplicado responde `409` con el código `PRODUCT_ALREADY_EXISTS` y un detalle que incluye el `productId` del producto existente. La garantía está en la base de datos (índice único sobre `lower(btrim(name))`), no solo en el código.
- **RN-03 · Idempotencia primero.** Repetir la misma `Idempotency-Key` con el mismo cuerpo reproduce la respuesta original (`201`); no es un duplicado. Solo una clave distinta con el mismo nombre es `409`.
- **RN-04 · Contrato HTTP estricto.** Un cuerpo HTTP con una propiedad que el contrato no define responde `400 VALIDATION_FAILED` con el detalle `Propiedad desconocida «<nombre>»; las permitidas son: <lista>`. Aplica a `POST /products` y `POST /orders` (y a sus líneas). Los mensajes de Kafka **no** son estrictos: siguen tolerando campos nuevos, porque los cambios de contrato son aditivos.
- **RN-05 · Los ids los genera el servidor.** El `productId` y el `orderId` se generan siempre en el servidor. Como consecuencia de RN-04, enviarlos en el cuerpo es un `400`.
- **RN-06 · Datos existentes.** La migración `V4` crea el índice único y falla si ya hay nombres repetidos. Antes de aplicarla en un entorno con datos, se borran los repetidos (en desarrollo se conserva el primero de cada nombre y se descartan los demás; los pedidos y reservas que los referencian se descartan con el volumen de datos de prueba).

## Criterios de aceptación

### 011-AC-01 · Un producto repetido se rechaza (REQ-22)
- Given un producto `Teclado` ya registrado
- When llega `POST /products` con otra `Idempotency-Key` y el nombre `Teclado`
- Then responde `409` con `code = PRODUCT_ALREADY_EXISTS` y el detalle trae el `productId` existente, y no se crea ninguna fila nueva

### 011-AC-02 · La comparación ignora mayúsculas y espacios (REQ-22)
- Given un producto `Teclado` registrado
- When llega un `POST /products` con `  teclado  ` o con `TECLADO`, o con `teclado   gamer` cuando ya existe `Teclado Gamer` (variantes de mayúsculas y de espacios en los extremos y entre palabras)
- Then responde `409 PRODUCT_ALREADY_EXISTS`

### 011-AC-03 · El nombre se guarda normalizado (REQ-22)
- Given un `POST /products` con el nombre `  Teclado    Gamer  `
- Then responde `201` y el nombre devuelto y guardado es `Teclado Gamer`

### 011-AC-04 · Los caracteres de control se rechazan (REQ-22)
- Given un nombre con un carácter de control (por ejemplo `\u0007`)
- When llega `POST /products`
- Then responde `400 VALIDATION_FAILED` y no se crea el producto

### 011-AC-05 · Dos solicitudes simultáneas con el mismo nombre crean uno solo (REQ-22, REQ-31)
- Given dos solicitudes simultáneas con claves distintas y el mismo nombre
- When llegan al mismo tiempo
- Then exactamente una responde `201` y la otra `409`, y existe un solo producto con ese nombre

### 011-AC-06 · La misma clave con el mismo cuerpo no es un duplicado (REQ-24)
- Given un `POST /products` ya procesado
- When se repite con la misma `Idempotency-Key` y el mismo cuerpo
- Then reproduce el `201` original, sin `409`

### 011-AC-07 · Las propiedades desconocidas se rechazan en la API (REQ-19, REQ-22)
- Given un cuerpo de `POST /products` con `productId` propio
- Then responde `400 VALIDATION_FAILED` con `Propiedad desconocida «productId»` y la lista de las permitidas
- Given un cuerpo de `POST /orders` con `orderId` propio, o una línea con una propiedad extra
- Then responde `400 VALIDATION_FAILED` con la propiedad desconocida

### 011-AC-08 · Los mensajes de Kafka siguen tolerando campos nuevos (REQ-26)
- Given un sobre de Kafka con un campo adicional que el consumidor no conoce
- When llega a `ms-inventario` o a `ms-pedidos`
- Then se procesa igual que sin el campo

### 011-AC-09 · La base de datos impide el duplicado aunque el código falle (REQ-16)
- Given un producto `Teclado` guardado
- When se inserta directamente otro con el nombre ` TECLADO `
- Then la base lo rechaza por el índice único

## Casos borde
- Un nombre formado solo por espacios queda vacío tras normalizar y se rechaza como vacío.
- El detalle del `409` no repite el nombre completo de más de 120 caracteres: el nombre ya está acotado por RN-01.

## Fuera de alcance
- Comparar sin acentos (`Raton` y `Ratón` siguen siendo distintos).
- Verificar en `ms-pedidos` que los productos existan antes de aceptar el pedido (el rechazo sigue ocurriendo de forma asíncrona en `ms-inventario`).
- Limitar el tamaño del cuerpo de las solicitudes.

## Preguntas abiertas
Ninguna.
