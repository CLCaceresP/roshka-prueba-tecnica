---
description: Corre la suite concurrente (Testcontainers) y reporta el resultado por escenario
allowed-tools: Read, Glob, Grep, Bash(./mvnw:*), Bash(docker info:*), Bash(docker ps:*)
---

Ejecuta la suite de pruebas concurrentes y reporta.

1. Verifica que Docker responde (`docker info`). Si no responde, detente y pide arrancar Docker Desktop. No desactives tests.
2. Ejecuta `./mvnw verify -Dgroups=concurrency`. Si los puertos de `docker-compose.yml` están libres, agrega `-Pe2e` para incluir el escenario 1 de punta a punta (`pruebas-sistema`).
3. Reporta una tabla por escenario:

| # | Escenario | Requisitos | Resultado | Estado final observado |
|---|---|---|---|---|
| 1 | 100 pedidos por las últimas 10 unidades | REQ-13, REQ-31 | | CONFIRMED / REJECTED / available / reserved |
| 2 | 50 requests con la misma `Idempotency-Key` | REQ-24, REQ-31 | | pedidos creados / respuestas distintas |
| 3 | Cada mensaje entregado 2–3 veces | REQ-14, REQ-26 | | diferencias contra una entrega única |
| 4 | Cancelación vs. reserva en todos los órdenes | REQ-27 | | estado final y stock por caso |
| 5 | Dos instancias de consumidor | REQ-02 | | mismas garantías |

4. Ante un fallo: muestra la aserción, el estado final y la semilla o los IDs para reproducirlo. Clasifica la causa: bug de producción, test flaky o infraestructura.
5. **Nunca** modifiques un test para que pase ni reintentes hasta obtener verde. Un test intermitente en esta suite es un bug hasta que se demuestre lo contrario.
