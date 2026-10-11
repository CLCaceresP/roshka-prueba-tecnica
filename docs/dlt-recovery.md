# Reprocesar una liberación de stock desde la DLT

Requisitos: REQ-12, REQ-18, REQ-26, REQ-27.

El reproceso es manual. Si un pedido confirmado se cancela y `LiberarStock` agota sus reintentos, el pedido puede estar `CANCELLED` mientras inventory conserva una reserva `RESERVED`. Recuperar la dependencia que falló no reenvía por sí solo los registros de la DLT. El timeout de saga solo revisa pedidos `PENDING`.

Este procedimiento recupera **un mensaje seleccionado**, conserva su key, `idMensaje`, payload y correlación, y permite comprobar el resultado. No se cambian offsets de los grupos de consumidores ni se modifican tablas de negocio. La DLT conserva el registro como evidencia; conviene registrar sus coordenadas y el resultado de cada intervención.

## 1. Identificar el mensaje y corregir la causa

Consultar `GET /orders/{orderId}` y `GET /products/{productId}/stock` de cada producto del pedido. Guardar el stock anterior y las cantidades que deben volver a `available`.

Revisar los logs del consumidor y los headers `kafka_dlt-*`. Restaurar la base de inventory y comprobar que su readiness vuelve a `UP` en `http://localhost:8091/actuator/health/readiness`. Si el error corresponde a un contrato inválido, corregir su causa antes de reenviar.

Desde la raíz del repositorio, inspeccionar la DLT con las herramientas incluidas en el broker:

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic inventario.comandos.DLT \
  --from-beginning --max-messages 20 --timeout-ms 10000 \
  --property print.partition=true --property print.offset=true \
  --property print.key=true --property print.headers=true
```

En PowerShell se usa el mismo comando en una línea. El timeout puede finalizar la inspección con código distinto de cero si hay menos de 20 registros; no publica mensajes.

Elegir un registro cuyo `tipo` sea `LiberarStock`, cuyo `carga.idPedido` coincida con el pedido y cuya key coincida con `idAgregado`. Anotar su **partición y offset en la DLT**, que son los impresos por el consumidor; el header `kafka_dlt-original-offset` corresponde al topic original y no sirve para seleccionar el registro de la DLT. También anotar el `idMensaje` original. Los ejemplos siguientes usan partición `0` y offset `42`: reemplazarlos por los valores observados.

## 2. Extraer y revisar un único registro

Git Bash / Linux / macOS:

```bash
replay_file="$(mktemp)"
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic inventario.comandos.DLT \
  --partition 0 --offset 42 --max-messages 1 --timeout-ms 10000 \
  --property print.key=true --property print.value=true \
  --property 'key.separator=|' > "$replay_file"

cat "$replay_file"
```

PowerShell:

```powershell
$replayRecord = @(docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic inventario.comandos.DLT --partition 0 --offset 42 --max-messages 1 --timeout-ms 10000 --property print.key=true --property print.value=true --property 'key.separator=|')
if ($LASTEXITCODE -ne 0 -or $replayRecord.Count -ne 1) { throw 'No se extrajo exactamente un registro' }
$replayRecord[0]
```

El contenido debe ser una sola línea `idPedido|{sobre JSON}`. Continuar solo si la extracción terminó con código cero y produjo exactamente un registro. Revisar que sea el `LiberarStock` seleccionado y que su key y `idMensaje` sigan intactos. No copiar la salida de la inspección anterior: contiene metadatos y headers que no pertenecen al payload. No generar otro `idMensaje` ni reconstruir el comando desde las cantidades del pedido.

## 3. Publicar el registro original

Git Bash / Linux / macOS, después de revisar el archivo:

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic inventario.comandos \
  --sync --producer-property acks=all --producer-property enable.idempotence=true \
  --property parse.key=true --property 'key.separator=|' < "$replay_file"
```

PowerShell, después de revisar `$replayRecord`:

```powershell
$previousEncoding = $OutputEncoding
try {
  $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
  $replayRecord | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic inventario.comandos --sync --producer-property acks=all --producer-property enable.idempotence=true --property parse.key=true --property 'key.separator=|'
  if ($LASTEXITCODE -ne 0) { throw 'El broker no confirmó el reproceso' }
} finally {
  $OutputEncoding = $previousEncoding
}
```

Estos comandos preservan key y envelope. Los headers Kafka de trazado y de DLT no se copian; la correlación de negocio sigue en el envelope.

## 4. Verificar que inventory aplicó la liberación

Esperar el procesamiento y consultar nuevamente el stock de cada producto. Para las líneas de esa reserva, `available` debe aumentar en la cantidad liberada y `reserved` disminuir en la misma cantidad, considerando otras operaciones concurrentes. El pedido sigue `CANCELLED` o `REJECTED`.

Buscar en los logs de inventory el `idMensaje` original con `result=APLICADO`, y la respuesta `StockLiberado` con `desenlace=DEVUELTO`; `NADA_QUE_LIBERAR` o `TOMBSTONE_REGISTRADO` también pueden ser correctos si la reserva ya estaba liberada o nunca llegó a crearse. Para comprobar una reserva concreta, el operador puede consultar:

```bash
docker compose exec -T postgres psql -U inventory_user -d inventory_db \
  -c "select order_id, status from reservations where order_id = '<orderId>';"
```

El resultado esperado es `RELEASED` o `REJECTED`, nunca `RESERVED` para el pedido cancelado o rechazado. Si vuelve a la DLT, revisar el nuevo fallo antes de repetir. Si se repite el mismo registro después de un éxito, la deduplicación por `idMensaje` evita una segunda liberación.

La prueba `InventarioResilienciaIT.debe_devolver_el_stock_una_vez_cuando_la_liberacion_fallida_se_reproduce_desde_mensajes_muertos` reproduce el agotamiento de reintentos, el stock retenido, la recuperación y dos reenvíos del mensaje original contra PostgreSQL y Kafka reales.
