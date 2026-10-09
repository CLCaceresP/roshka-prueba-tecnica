# Requisitos del enunciado

Fuente de verdad: [`prueba-tecnica.pdf`](./prueba-tecnica.pdf). Este documento enumera cada requisito del PDF con un identificador estable. Toda spec en `specs/` y todo test de aceptación referencia los identificadores que cubre. La matriz requisito → spec → test se publica en el `README.md`.

Si este documento y el PDF difieren, gana el PDF.

Restricciones de entrega del PDF: tiempo estimado de 3 a 4 días, Java sugerido, Spring Boot sugerido, entrega en un repositorio de GitHub con instrucciones de ejecución.

## Generales

| ID | Requisito | Cita del PDF |
|---|---|---|
| REQ-01 | Al menos dos microservicios desarrollados con Spring Boot. | "La solución debe contar con al menos dos microservicios desarrollados con Spring Boot." |
| REQ-02 | Soportar solicitudes concurrentes, múltiples instancias, mensajes duplicados, errores temporales y comunicación asincrónica entre servicios. | Visión general |

## Order Service

| ID | Requisito |
|---|---|
| REQ-03 | Crear pedidos. |
| REQ-04 | Consultar el estado de un pedido. |
| REQ-05 | Cancelar pedidos. |
| REQ-06 | Publicar eventos relacionados con pedidos. |
| REQ-07 | Actualizar el estado del pedido según las respuestas recibidas. |
| REQ-08 | Estados posibles: `PENDING`, `CONFIRMED`, `REJECTED`, `CANCELLED`. |
| REQ-09 | Operaciones idempotentes en Order Service. |

## Inventory Service

| ID | Requisito |
|---|---|
| REQ-10 | Mantener el stock disponible de cada producto. |
| REQ-11 | Reservar stock. |
| REQ-12 | Liberar stock por cancelaciones. |
| REQ-13 | Evitar que el stock quede negativo. |
| REQ-14 | Procesar correctamente eventos repetidos. |
| REQ-15 | Operaciones idempotentes en Inventory Service. |

## Datos y mensajería

| ID | Requisito |
|---|---|
| REQ-16 | Cada microservicio tiene su propia base de datos o esquema; no se comparten tablas entre servicios. |
| REQ-17 | Sistema de mensajería (Kafka, RabbitMQ u otra alternativa equivalente). |
| REQ-18 | El README explica la elección del sistema de mensajería: motivo, garantía de entrega, estrategia de reintentos, manejo de mensajes que no pueden procesarse y orden de procesamiento cuando corresponda. |

## Endpoints mínimos

| ID | Requisito |
|---|---|
| REQ-19 | Order Service — carga de pedido: `POST /orders`. |
| REQ-20 | Order Service — consulta de pedido: `GET /orders/{orderId}`. |
| REQ-21 | Order Service — cancelación: `POST /orders/{orderId}/cancel` o `DELETE /orders/{orderId}`. Se implementa `DELETE /orders/{orderId}`. |
| REQ-22 | Inventory Service — carga de stock: `POST /products`. |
| REQ-23 | Inventory Service — consulta de stock: `GET /products/{productId}/stock`. |

## Garantías transversales

| ID | Requisito |
|---|---|
| REQ-24 | Idempotencia en endpoints de escritura. |
| REQ-25 | Bloqueo distribuido. |
| REQ-26 | La mensajería se considera at-least-once: un mismo evento puede llegar dos o más veces. |
| REQ-27 | Un pedido puede cancelarse mientras la reserva de stock está en procesamiento; el estado final debe ser consistente. |

## Entregables

| ID | Requisito |
|---|---|
| REQ-28 | Repositorio con código fuente e instrucciones para desplegar y probar el proyecto. |
| REQ-29 | Tests unitarios. |
| REQ-30 | Stack de pruebas de integración con Postman, Bruno, Insomnia o similar. |
| REQ-31 | Pruebas concurrentes. |

## Bonus (no obligatorios según el PDF)

| ID | Bonus |
|---|---|
| BON-01 | Patrones de diseño. |
| BON-02 | Arquitectura. |
| BON-03 | Observabilidad y trazabilidad. |
| BON-04 | Configuración de agentes (skills, commands, rules, AGENTS/CLAUDE.md, TDD, SDD, etc.). |

## Requisitos estructurales sin spec de feature

Estos requisitos no describen comportamiento del sistema, sino su forma o sus entregables. Se cubren aquí:

| ID | Dónde se cubre | Fase |
|---|---|---|
| REQ-01 | Módulos `ms-pedidos` y `ms-inventario` (Spring Boot); mapa de módulos en `AGENTS.md` | 1 |
| REQ-29 | Regla `.claude/rules/testing.md`; tareas de nivel `unit` en cada `specs/*/tasks.md` | 2–4 |
| REQ-30 | Colección Postman en `postman/` (ejecutable con Newman), con un request por criterio de aceptación HTTP | 6 |
| BON-01 | `docs/patterns.md` | 6 |
| BON-02 | `docs/adr/`, arquitectura hexagonal verificada con ArchUnit, diagramas C4 en el README | 0–6 |
| BON-04 | `AGENTS.md`, `CLAUDE.md`, `.claude/` (rules, commands, agents, skills, hooks), `specs/` | 0 |

## Funcionalidad complementaria

No está en el PDF. No contradice ningún REQ ni invariante.

| ID | Extensión | Spec |
|---|---|---|
| EXT-01 | Listado paginado de productos y consulta de un producto (`GET /products`, `GET /products/{productId}`). | [012](../../specs/012-listado-de-productos/spec.md) |
| EXT-02 | Listado paginado de pedidos por rango de fechas `MM/dd/yyyy` (`GET /orders`). | [013](../../specs/013-pedidos-por-fecha/spec.md) |
| EXT-03 | Ajuste del stock disponible (`PUT /products/{productId}/stock`) con historial de movimientos. | [014](../../specs/014-ajuste-de-stock/spec.md) |
| EXT-04 | Documentación OpenAPI completa de todos los endpoints, servida por el gateway. | [015](../../specs/015-documentacion-openapi/spec.md) |

## Interpretaciones registradas

- **REQ-16**: se usan dos bases de datos (`orders_db`, `inventory_db`) en una misma instancia de Postgres, cada una con su propio usuario y sin permisos cruzados.
- **REQ-21**: el PDF admite `POST .../cancel` o `DELETE`; se elige `DELETE /orders/{orderId}`, el verbo HTTP que corresponde a la operación. El pedido cancelado sigue existiendo y se puede consultar con `GET`; exige `Idempotency-Key` como toda escritura.
- **REQ-22**: `POST /products` crea un producto con stock inicial e identificador generado por el servidor. El ajuste de stock de un producto existente se agrega como funcionalidad complementaria (EXT-03); no hay estado habilitado/deshabilitado de producto.
- **REQ-30**: se usa Postman, ejecutable por línea de comandos con Newman (`scripts/postman.sh`).
