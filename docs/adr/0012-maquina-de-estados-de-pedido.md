# 0012 · Máquina de estados de Order con el patrón State sobre un enum

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-05, REQ-07, REQ-08, REQ-27

## Contexto y problema

`Pedido` tiene cuatro estados y cuatro entradas: cancelación, `StockReservado`, `StockRechazado` y timeout de saga. La spec 004 define la tabla completa estado × entrada, incluidas las reacciones a eventos tardíos (compensación) y las combinaciones imposibles. Esa tabla es la parte más delicada del sistema: tiene que leerse y probarse celda por celda.

## Factores de decisión

- Cada celda de la tabla de la spec 004 tiene que verse en un solo lugar del código.
- Las transiciones viven en el dominio, sin frameworks (ArchUnit).
- Una combinación nueva tiene que obligar a decidir su comportamiento: que el compilador no deje una celda sin definir.

## Opciones consideradas

1. **`switch` en cada método de `Pedido`.** Cuatro métodos, cada uno con un `switch` sobre el estado. La tabla queda repartida por columnas.
2. **Tabla de transiciones en datos** (`Map<(estado, entrada), transición>`). Compacta, pero las excepciones y las compensaciones quedan como datos opacos.
3. **Patrón State con una clase por estado** (interfaz + 4 implementaciones). Ceremonia sin beneficio: los estados no tienen datos propios.
4. **Patrón State sobre un `enum`** con un método abstracto por entrada.

## Decisión

**Opción 4.** `EstadoPedido` es un `enum` y cada constante implementa `alCancelar()`, `alStockReservado()`, `alStockRechazado()` y `alTimeout()`. Cada método devuelve una `Transition`: el estado destino y, si corresponde, la causa de liberación de stock (`CausaLiberacion`).

- Al ser abstractos, el compilador obliga a definir las cuatro entradas en cada estado.
- Leer una constante del enum equivale a leer una fila de la tabla de la spec 004.
- `Pedido` aplica la transición, registra los **eventos de dominio** (`PedidoConfirmado`, `PedidoCancelado`, `LiberacionStockSolicitada`, ...) y el orquestador de la saga los traduce a comandos ([0004](0004-saga-orquestada-dentro-de-ms-pedidos.md)).
- Las combinaciones imposibles (`StockRechazado` sobre `CONFIRMED`) lanzan `TransicionPedidoInvalida`, que es no reintentable y termina en la DLT ([0006](0006-reintentos-bloqueantes-y-dlt.md)). Cancelar un `REJECTED` lanza `PedidoNoCancelable` (`409`).

## Consecuencias

- Positivas: la tabla de la spec queda en el código casi literal y se prueba con un test parametrizado por celda.
- Negativas: agregar una entrada nueva obliga a tocar las cuatro constantes. Es intencional.

## Cómo se verifica

`EstadoPedidoTest` parametrizado con las 16 celdas de la tabla estado × entrada de la spec 004.
