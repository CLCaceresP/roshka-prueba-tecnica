# 011 · Plan

## Componentes

| Pieza | Dónde | Qué |
|---|---|---|
| `Producto.registrar` | `dominio` de `ms-inventario` | Normaliza el nombre (recorte, colapso de espacios, rechazo de caracteres de control) antes de validar la longitud. |
| `ProductoDuplicado` | `dominio` de `ms-inventario` | Excepción de dominio con el nombre y, si se conoce, el `productId` existente. |
| `ProductoRepository.buscarPorNombre` | puerto de salida | Busca por nombre normalizado sin distinguir mayúsculas. |
| `RegistroProducto` | `aplicacion` | Paso «Nombre verificado (sin duplicados)» antes de guardar. |
| `JdbcProductoRepository.insertar` | `adaptadores/salida/persistencia` | Traduce la violación del índice `products_name_unique` en `ProductoDuplicado` (carrera entre solicitudes). |
| `V4__producto_nombre_unico.sql` | `ms-inventario` | `create unique index products_name_unique on products (lower(btrim(name)))`. |
| `ErroresApi` | `adaptadores/entrada/rest` | `ProductoDuplicado` → `409 PRODUCT_ALREADY_EXISTS`; `HttpMessageNotReadableException` con propiedad desconocida → `400 VALIDATION_FAILED` con el detalle. |
| `spring.jackson.deserialization.fail-on-unknown-properties` | `application.yml` de ambos servicios | `true` para la API HTTP. |
| `LectorMensajes` | `adaptadores/entrada/mensajeria` de ambos servicios | Usa una copia del mapper con `FAIL_ON_UNKNOWN_PROPERTIES` desactivado: Kafka sigue siendo tolerante. |

## Modelo de datos y mensajes

Migración nueva `V4` en `ms-inventario`. Sin cambios en el contrato de mensajes ni en `ms-pedidos`.

## ADRs

- [0020](../../docs/adr/0020-unicidad-del-nombre-y-contrato-http-estricto.md)

## Estrategia de tests

| Criterio | Nivel | Cómo |
|---|---|---|
| 011-AC-03, 011-AC-04 | unit (`ProductoTest`) | Normalización y caracteres de control. |
| 011-AC-01, 011-AC-02 | unit (`RegistroProductoTest`, fake con la misma regla) y it (`ProductoApiIT`) | 409 con el id existente. |
| 011-AC-06, 011-AC-07 | it (`ProductoApiIT`, `PedidoApiIT`) | Replay idempotente y 400 por propiedades desconocidas. |
| 011-AC-05 | concurrencia (`InventarioConcurrenciaIT`) | Dos hilos liberados juntos con el mismo nombre. |
| 011-AC-08 | it (`InventarioMensajeriaIT`, `PedidoMensajeriaIT`) | Sobre con un campo extra. |
| 011-AC-09 | it (`InventarioPersistenciaIT`) | Insert directo con el nombre en otra forma. |

Los tests de integración que crean productos con nombres literales pasan a usar nombres únicos (`NombresUnicos`).
