# 0020 · Nombre de producto único por índice y contrato HTTP estricto

- Estado: Aceptado
- Fecha: 2026-10-10
- Requisitos: REQ-22, REQ-24, REQ-19, REQ-16

## Contexto y problema

`POST /products` permite registrar el mismo producto muchas veces, y las dos APIs aceptan en silencio campos que el contrato no define. El enunciado no dice que el nombre deba ser único, pero un catálogo con el mismo producto repetido hace ambiguo qué stock se reserva, y ocultar campos desconocidos esconde errores del cliente (por ejemplo, creer que puede elegir el id).

## Factores de decisión

- Las invariantes se expresan en la base de datos, no solo en el código (regla del proyecto, ADR 0009).
- Dos solicitudes simultáneas no pueden crear el mismo nombre.
- Las migraciones ya commiteadas son inmutables (invariante 14): el cambio de esquema es una migración nueva.
- Los mensajes de Kafka deben seguir tolerando campos nuevos (contrato aditivo, `AGENTS.md`).
- Datos de desarrollo existentes con nombres repetidos.

## Opciones consideradas para la unicidad

1. **Índice único sobre `lower(btrim(name))` más validación previa en la aplicación (elegida).** La base garantiza la regla incluso con carreras; la validación previa da un `409` claro con el `productId` existente.
2. **Solo validar en la aplicación.** Más simple, pero dos solicitudes simultáneas pueden crear el mismo nombre; contradice la regla de expresar las invariantes en la base.
3. **Columna `name_key` generada, con índice único.** Equivalente en garantía, pero agrega una columna solo para el índice.
4. **Nombre exacto, sin normalizar.** No evita `Teclado` y `teclado`, que para un usuario son el mismo producto.

## Opciones consideradas para el contrato HTTP estricto

1. **`FAIL_ON_UNKNOWN_PROPERTIES` activado en el mapper global y un mapper tolerante solo para Kafka (elegida).** Una línea de configuración para la API; `LectorMensajes` usa `rebuild()` con la característica desactivada.
2. **Anotar cada DTO.** Jackson no ofrece una anotación que fuerce el fallo en una clase cuando la configuración global lo tolera; habría que repetir lógica por DTO.
3. **Un `HttpMessageConverter` propio para la API.** Más código y más superficie para el mismo resultado.
4. **No hacer nada.** Descartada: es el comportamiento que se quiere corregir.

## Decisión

- Migración `V4__producto_nombre_unico.sql` en `ms-inventario`: `create unique index products_name_unique on products (lower(btrim(name)))`. Falla si ya hay repetidos; se limpian antes en desarrollo.
- `Producto.registrar` normaliza el nombre (recorta, colapsa espacios, rechaza caracteres de control) antes de medir la longitud.
- `RegistroProducto` verifica el nombre antes de guardar (`ProductoRepository.buscarPorNombre`); si otra solicitud se adelanta, el adaptador traduce la violación del índice en la misma `ProductoDuplicado`.
- `ProductoDuplicado` responde `409` con el código `PRODUCT_ALREADY_EXISTS`. La idempotencia se evalúa antes: la misma clave con el mismo cuerpo reproduce el `201`.
- `spring.jackson.deserialization.fail-on-unknown-properties: true` en ambos servicios. `ErroresApi` traduce `UnrecognizedPropertyException` en `400 VALIDATION_FAILED` con las propiedades permitidas. `LectorMensajes` toma una copia tolerante del mapper.
- Sin comparación sin acentos: `Raton` y `Ratón` son distintos.

## Consecuencias

**Positivas**
- No hay productos duplicados por nombre, ni siquiera con solicitudes simultáneas.
- Los errores de contrato del cliente se ven de inmediato.

**Negativas y riesgos**
- Es un cambio de comportamiento observable de la API: quien repetía nombres (scripts, el front local, pruebas manuales) ahora recibe `409`. Mitigación: los tests, la colección de Postman y k6 ya usan nombres únicos; se documenta en el README.
- La migración falla en bases con repetidos. Mitigación: limpieza previa documentada; un entorno limpio no se ve afectado.
- Un cliente que enviaba campos extra empieza a recibir `400`. Mitigación: el detalle nombra la propiedad y la lista de las permitidas.
- Un `ProductoDuplicado` por carrera no incluye el id del existente (la transacción ya está abortada para consultarlo).
- Un error de configuración que active el modo estricto en Kafka rompería los consumidores ante campos aditivos. Mitigación: el test 011-AC-08 lo detecta.

## Cómo se verifica

- Tests unitarios de normalización y de la regla de unicidad, ITs de la API y de persistencia, y la prueba de concurrencia (spec 011).

## Referencias

- [Spec 011](../../specs/011-validaciones-de-producto-y-contrato-http/spec.md) · [ADR 0009](0009-anti-sobreventa-en-sql.md)
