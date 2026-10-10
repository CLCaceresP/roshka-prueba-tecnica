import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { parseArgs } from 'node:util';

const { values: opciones } = parseArgs({
  options: { puerto: { type: 'string', default: '5173' }, gateway: { type: 'string', default: 'http://localhost:8080' } },
});
const puerto = Number(opciones.puerto);
const gateway = new URL(opciones.gateway);
if (!Number.isInteger(puerto) || puerto < 1 || puerto > 65535 || !['http:', 'https:'].includes(gateway.protocol)) {
  throw new Error('Puerto o dirección del gateway inválidos.');
}
const archivos = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ['/app.js', ['app.js', 'text/javascript; charset=utf-8']],
  ['/estilos.css', ['estilos.css', 'text/css; charset=utf-8']],
]);
const uuid = '[0-9a-fA-F-]{36}';
const rutas = new Map([
  ['GET', new RegExp(`^/(products(?:/${uuid}(?:/stock(?:/movements)?)?)?|orders(?:/${uuid})?|actuator/health)$`)],
  ['POST', new RegExp(`^/(products|orders)$`)],
  ['PUT', new RegExp(`^/products/${uuid}/stock$`)],
  ['DELETE', new RegExp(`^/orders/${uuid}$`)],
]);
const limiteCuerpo = 1024 * 1024;
const tiempoEsperaMs = 15000;

function responderError(respuesta, estado, detalle) {
  respuesta.writeHead(estado, { 'Content-Type': 'application/problem+json; charset=utf-8' });
  respuesta.end(JSON.stringify({ status: estado, title: 'No se pudo completar la solicitud', detail: detalle }));
}

const servidor = createServer(async (solicitud, respuesta) => {
  try {
    const direccion = new URL(solicitud.url, 'http://localhost');
    const ruta = direccion.pathname;
    if (ruta.startsWith('/api/')) {
      const destino = ruta.slice(4);
      if (!rutas.get(solicitud.method)?.test(destino)) {
        responderError(respuesta, 404, 'Ruta no disponible en el front de pruebas.');
        return;
      }
      if (solicitud.method !== 'GET' && solicitud.headers.origin &&
          solicitud.headers.origin !== `http://${solicitud.headers.host}`) {
        responderError(respuesta, 403, 'Abre el front desde su dirección local para enviar solicitudes.');
        return;
      }
      const cabeceras = { Accept: 'application/json' };
      for (const nombre of ['content-type', 'idempotency-key', 'x-correlation-id']) {
        if (solicitud.headers[nombre]) cabeceras[nombre] = solicitud.headers[nombre];
      }
      const fragmentos = [];
      let tamanio = 0;
      for await (const fragmento of solicitud) {
        tamanio += fragmento.length;
        if (tamanio > limiteCuerpo) {
          responderError(respuesta, 413, 'El cuerpo de la solicitud es demasiado grande.');
          return;
        }
        fragmentos.push(fragmento);
      }
      const cuerpo = Buffer.concat(fragmentos).toString('utf8');
      const resultado = await fetch(new URL(destino + direccion.search, gateway), {
        method: solicitud.method, headers: cabeceras,
        body: solicitud.method !== 'GET' && cuerpo ? cuerpo : undefined,
        signal: AbortSignal.timeout(tiempoEsperaMs),
      });
      const contenido = Buffer.from(await resultado.arrayBuffer());
      const cabecerasRespuesta = { 'Cache-Control': 'no-store' };
      for (const nombre of ['content-type', 'location', 'x-correlation-id', 'retry-after', 'idempotent-replayed']) {
        if (resultado.headers.has(nombre)) cabecerasRespuesta[nombre] = resultado.headers.get(nombre);
      }
      respuesta.writeHead(resultado.status, cabecerasRespuesta);
      respuesta.end(contenido);
      return;
    }
    const archivo = archivos.get(ruta);
    if (solicitud.method !== 'GET' || !archivo) {
      responderError(respuesta, 404, 'Página no encontrada.');
      return;
    }
    const contenido = await readFile(new URL(archivo[0], import.meta.url));
    respuesta.writeHead(200, {
      'Content-Type': archivo[1], 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
      'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'",
    });
    respuesta.end(contenido);
  } catch (error) {
    responderError(respuesta, 502, error.name === 'TimeoutError'
      ? 'El gateway tardó demasiado en responder. Puedes consultar el pedido o repetir la misma solicitud.'
      : `No se pudo conectar al gateway ${gateway.origin}. Comprueba que el stack esté encendido.`);
  }
});
servidor.on('error', (error) => {
  console.error(error.code === 'EADDRINUSE' ? `El puerto ${puerto} está ocupado. Usa --puerto con otro número.` : error.message);
  process.exitCode = 1;
});
servidor.listen(puerto, '127.0.0.1', () => {
  console.log(`Front: http://localhost:${puerto}\nGateway: ${gateway.origin}\nCtrl+C cierra solamente el front.`);
});
