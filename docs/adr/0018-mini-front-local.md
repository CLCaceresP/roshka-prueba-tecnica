# ADR 0018: mini front local sin dependencias

Estado: aceptada. Fecha: 2026-10-10.

## Contexto

Se necesita probar visualmente los endpoints existentes con el stack local ya encendido, sin complicar el proyecto.

## Decisión

Usar HTML, CSS y JavaScript, servidos por un servidor Node.js que escucha en loopback y reenvía un conjunto explícito de rutas al gateway, conservando parámetros de consulta y cabeceras de idempotencia. No se añaden paquetes. El cliente conserva las solicitudes de creación de pedidos y ajustes para repetirlas con la misma clave y consulta la saga hasta un estado terminal con un límite de espera.

Con los endpoints de las specs 012 a 014, el catálogo, los pedidos por fecha y los movimientos se consultan con paginación. El front no persiste listas en el navegador y deshabilita la caché HTTP. Las reglas de negocio y los errores siguen siendo responsabilidad del backend.

## Alternativas y consecuencias

Un framework con compilación añade dependencias innecesarias para esta herramienta local. Una página abierta como archivo requiere cambios de CORS; servirla desde Java obliga a recompilar y reiniciar el gateway. El proxy local permite usar el stack sin esos cambios. Se arranca por separado con Node.js.
