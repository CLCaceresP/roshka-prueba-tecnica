# 0004 · Saga orquestada dentro de ms-pedidos

- Estado: Aceptado
- Fecha: 2026-10-09
- Requisitos: REQ-03, REQ-05, REQ-06, REQ-07, REQ-12, REQ-27

## Contexto y problema

Crear un pedido es una transacción distribuida: el pedido vive en `orders_db` y el stock en `inventory_db` (REQ-16). No hay una transacción ACID común. El flujo tiene un paso con dos resultados (`StockReservado` / `StockRechazado`) y dos compensaciones posibles: la cancelación del usuario (REQ-05) y el timeout de saga. Además está la carrera cancelación/reserva (REQ-27), que exige decidir en un único lugar qué hacer cuando llega una respuesta tardía.

## Factores de decisión

- Un único lugar que conozca el estado de la saga y decida compensaciones.
- No agregar un deployable ni una base de datos más.
- Lógica de compensación verificable con tests unitarios.

## Opciones consideradas

1. **Coreografía.** Cada servicio reacciona a eventos del otro sin coordinador. Con un solo paso es casi equivalente, pero la lógica de "cancelado mientras se reservaba" o "timeout con reserva tardía" queda repartida entre los dos servicios.
2. **Orquestador como deployable separado.** Necesitaría su propia base con el estado de la saga, duplicando el estado del pedido, o consultar sincrónicamente a ms-pedidos. Suma un salto de red y un punto de falla sin aportar nada con dos participantes.
3. **Orquestador dentro de ms-pedidos** (paquete `aplicacion/saga`).
4. **Motor de workflows** (Temporal, Camunda). Desproporcionado para la prueba y agrega infraestructura.

## Decisión

**Opción 3.** El orquestador vive en `ms-pedidos`, en `aplicacion/saga`:

- El estado de la saga **es** el estado del agregado `Pedido`, más `saga_started_at` para el timeout. No hay una tabla de saga aparte.
- El orquestador reacciona a eventos de inventory y a comandos del usuario, delega la transición en el dominio (patrón State) y decide el siguiente comando (`LiberarStock` como compensación). Lo escribe en el outbox, nunca en Kafka directamente.
- El dominio decide la transición. El orquestador decide los efectos: qué comando emitir ante cada resultado.

## Consecuencias

- Positivas: la tabla de decisiones de la carrera (spec 004) vive en un solo lugar y se prueba sin infraestructura. No hay estado duplicado. Un servicio menos.
- Negativas: ms-pedidos conoce el protocolo de inventory (comandos y eventos), lo que es inherente a la orquestación. Si la saga creciera (pagos, envío), habría que reevaluar extraerla o migrar a un motor de workflows.

## Cómo se verifica

- Tests unitarios de la tabla estado × evento → (nuevo estado, comandos emitidos) de la spec 004.
- `architecture-reviewer` y ArchUnit: `aplicacion/saga` no depende de adapters.

## Referencias

- Chris Richardson, *Microservices Patterns*, cap. 4 "Managing transactions with sagas".
