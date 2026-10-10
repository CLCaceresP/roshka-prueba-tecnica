# 010 · Tareas

- [x] T-01 · `Pasos`: cronometra y registra procesos y pasos, también cuando fallan · cubre 010-AC-04 · nivel: unit · módulo: ms-pedidos y ms-inventario
- [x] T-02 · Línea de entrada y salida de la solicitud con texto legible en los servicios y el gateway · cubre 010-AC-01 · nivel: it · módulo: ms-pedidos, ms-inventario, gateway-pedidos-inventario
- [x] T-03 · Colocar y cancelar pedido, idempotencia y bloqueos paso a paso · cubre 010-AC-02, 010-AC-04, 010-AC-05 · nivel: it · módulo: ms-pedidos
- [x] T-04 · Registrar producto, consultar stock, reservar y liberar paso a paso · cubre 010-AC-03, 010-AC-05 · nivel: it · módulo: ms-inventario
- [x] T-05 · Saga: aplicar respuesta y expirar paso a paso, `sagaDurationMs` · cubre 010-AC-05, 010-AC-06 · nivel: it · módulo: ms-pedidos
- [x] T-06 · Eventos recibidos, en proceso y finalizados con sus detalles en MDC; publicación del outbox y DLT con detalles · cubre 010-AC-05 · nivel: it · módulo: ambos
- [x] T-07 · Los ids de producto y pedido los genera el servidor · cubre 010-AC-07 · nivel: it · módulo: ambos
- [x] T-08 · Mensajes autoexplicativos: colocar, reserva aprobada o rechazada con cifras, pedido rechazado, confirmado y cancelado, `Location` en la salida · cubre 010-AC-09, 010-AC-10 · nivel: it · módulo: ambos
- [x] T-09 · Consultas que dicen qué devolvieron (`GET /orders/{orderId}`, `GET /products/{productId}/stock`) · cubre 010-AC-11 · nivel: it · módulo: ambos
- [x] T-10 · Errores HTTP explicados (`ErroresApi` en ambos servicios y rechazos propios del gateway) · cubre 010-AC-12 · nivel: it · módulo: ambos y gateway
- [x] T-11 · Trabajos en segundo plano: job de timeout, publisher del outbox, reintentos y DLT · cubre 010-AC-13 · nivel: it · módulo: ambos
- [x] T-12 · Bloqueos con sus tiempos · cubre 010-AC-05 · nivel: it · módulo: ambos
- [x] T-13 · Verificación del recorrido en Seq, README y reglas de logging · cubre 010-AC-08 · nivel: compose · módulo: infra
