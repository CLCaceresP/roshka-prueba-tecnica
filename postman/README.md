# Postman

1. Levantar el sistema con `scripts/up.sh` (o `scripts/up.ps1`).
2. En Postman: **Import** y elegir los dos archivos de esta carpeta.
3. Arriba a la derecha seleccionar el entorno **Local**.
4. Sobre la colección **Pedidos e inventario**: **Run** y **Run collection**.

Las requests van en orden y se pasan datos entre sí (el producto y el pedido que se crean al principio), así que conviene correr la colección completa. Algunas esperan 3 segundos porque la reserva de stock es asíncrona; el tiempo se cambia con `sagaWaitMs` en el entorno.

Por línea de comandos, sin abrir Postman (requiere Node.js 22 o superior):

```bash
scripts/postman.sh          # PowerShell: scripts\postman.ps1
```

Es equivalente a `npx newman run postman/pedidos-inventario.postman_collection.json -e postman/local.postman_environment.json`.
