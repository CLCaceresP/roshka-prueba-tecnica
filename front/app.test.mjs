import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createContext, runInContext } from 'node:vm';
import { setTimeout as esperar } from 'node:timers/promises';

const codigo = await readFile(new URL('./app.js', import.meta.url), 'utf8');
const html = await readFile(new URL('./index.html', import.meta.url), 'utf8');
const producto = { productId: '11111111-1111-4111-8111-111111111111', name: 'Teclado', available: 5, reserved: 0, total: 5 };
const eliminado = { ...producto, productId: '22222222-2222-4222-8222-222222222222', name: 'Mouse' };
const pedido = { orderId: '33333333-3333-4333-8333-333333333333', status: 'CONFIRMED',
  lines: [{ productId: producto.productId, quantity: 2 }], createdAt: '2026-10-10T12:00:00Z' };

class Elemento {
  value = '';
  textContent = '';
  disabled = false;
  className = '';
  hijos = [];
  opciones = [];
  eventos = {};
  classList = { toggle() {} };
  addEventListener(tipo, accion) { this.eventos[tipo] = accion; }
  replaceChildren(...hijos) { this.hijos = hijos; this.opciones = hijos; }
  add(opcion) { this.opciones.push(opcion); }
  append(...hijos) { this.hijos.push(...hijos); }
}

async function preparar({ productos = [producto], responder } = {}) {
  const elementos = new Map([...html.matchAll(/id="([^"]+)"/g)].map((coincidencia) => [coincidencia[1], new Elemento()]));
  for (const [id, valor] of Object.entries({ cantidad: '2', 'stock-inicial': '5', 'disponible-ajuste': '5' })) elementos.get(id).value = valor;
  const solicitudes = [];
  const botones = [...html.matchAll(/<button[^>]*id="([^"]+)"/g)].map((coincidencia) => elementos.get(coincidencia[1])).filter(Boolean);
  const contexto = createContext({
    document: { getElementById: (id) => elementos.get(id), querySelectorAll: () => botones, createElement: () => new Elemento() },
    Option: class { constructor(texto, valor) { this.text = texto; this.value = valor; } },
    crypto, AbortSignal, URLSearchParams,
    setTimeout: (accion, ms) => setTimeout(accion, Math.min(ms, 10)), clearTimeout,
    navigator: { clipboard: { async writeText(texto) { contexto.filtroCopiado = texto; } } },
    fetch: async (ruta, opciones) => {
      const url = new URL(ruta, 'http://front');
      solicitudes.push({ url, opciones });
      assert.equal(opciones.cache, 'no-store');
      if (url.pathname === '/api/actuator/health') return Response.json({ status: 'UP' });
      const personalizada = await responder?.(url, opciones);
      if (personalizada) return personalizada;
      if (url.pathname === '/api/products') {
        const pagina = Number(url.searchParams.get('page')); const tamanio = Number(url.searchParams.get('size'));
        return Response.json({ items: productos.slice(pagina * tamanio, (pagina + 1) * tamanio), page: pagina,
          size: tamanio, totalElements: productos.length, totalPages: Math.ceil(productos.length / tamanio) });
      }
      if (url.pathname === '/api/orders') return Response.json({ items: [], page: Number(url.searchParams.get('page')), totalElements: 0, totalPages: 0 });
      if (url.pathname === `/api/products/${producto.productId}` || url.pathname === `/api/products/${producto.productId}/stock`) return Response.json(producto);
      if (url.pathname.endsWith('/stock/movements')) return Response.json({ items: [], page: 0, totalElements: 0, totalPages: 0 });
      return Response.json({ code: 'PRODUCT_NOT_FOUND', detail: 'No existe el producto' }, { status: 404 });
    },
  });
  runInContext(codigo, contexto);
  async function esperarFin() {
    const limite = Date.now() + 2000;
    while (runInContext('ocupado', contexto)) {
      assert.ok(Date.now() < limite, 'La operación debe finalizar'); await esperar(5);
    }
  }
  await esperarFin();
  return {
    elementos, solicitudes, contexto,
    idsVisibles: () => elementos.get('productos').opciones.map((opcion) => opcion.value).filter(Boolean),
    accionar: async (id, tipo = 'click') => { elementos.get(id).eventos[tipo]({ preventDefault() {} }); await esperarFin(); },
    cerrar: () => runInContext('++generacionConsulta', contexto),
  };
}

test('EXT-01 debe_usar_catalogo_del_backend', async () => {
  const front = await preparar();
  assert.deepEqual(front.idsVisibles(), [producto.productId]);
  assert.equal(front.elementos.get('productos-anterior').disabled, true);
  assert.equal(front.elementos.get('productos-siguiente').disabled, true);
});

test('EXT-01 debe_recorrer_paginas_del_catalogo', async () => {
  const productos = Array.from({ length: 21 }, (_, indice) => ({ ...producto, productId: crypto.randomUUID(), name: `Producto ${indice}` }));
  const front = await preparar({ productos });
  assert.equal(front.idsVisibles().length, 20);
  assert.equal(front.elementos.get('productos-siguiente').disabled, false);
  await front.accionar('productos-siguiente');
  assert.deepEqual(front.idsVisibles(), [productos[20].productId]);
  assert.equal(front.elementos.get('productos-siguiente').disabled, true);
  assert.match(front.elementos.get('pagina-productos').textContent, /Página 2 de 2/);
});

test('EXT-01 debe_descartar_el_producto_visible_cuando_falla_la_actualizacion_del_catalogo', async () => {
  let fallar = false;
  const front = await preparar({ responder: (url) => {
    if (fallar && url.pathname === '/api/products') return Response.json({ detail: 'Sin conexión' }, { status: 502 });
  } });
  front.elementos.get('productos').value = producto.productId;
  await front.accionar('productos', 'change');
  assert.equal(front.elementos.get('disponible').textContent, 5);
  fallar = true;
  await front.accionar('actualizar-productos');
  assert.deepEqual(front.idsVisibles(), []);
  assert.equal(front.elementos.get('disponible').textContent, '—');
});

for (const estado of [502, 'sin-conexion']) test(`EXT-01 debe_vaciar_listado_y_permitir_reintentar_cuando_hay_${estado}`, async () => {
  let fallar = true;
  const front = await preparar({ responder: (url) => {
    if (url.pathname !== '/api/products' || !fallar) return;
    if (estado === 'sin-conexion') throw new TypeError('Sin conexión');
    return Response.json({ detail: 'Sin conexión' }, { status: estado });
  } });
  assert.deepEqual(front.idsVisibles(), []);
  fallar = false; await front.accionar('actualizar-productos');
  assert.deepEqual(front.idsVisibles(), [producto.productId]);
});

test('REQ-23 debe_limpiar_id_y_stock_cuando_el_producto_ya_no_existe', async () => {
  let existe = true;
  const front = await preparar({ responder: (url) => {
    if (!existe && url.pathname.endsWith('/stock')) return Response.json({ detail: 'No existe' }, { status: 404 });
  } });
  front.elementos.get('id-producto').value = producto.productId;
  await front.accionar('form-stock', 'submit');
  assert.equal(front.elementos.get('disponible').textContent, 5);
  existe = false; await front.accionar('form-stock', 'submit');
  assert.equal(front.elementos.get('id-producto').value, '');
  for (const id of ['disponible', 'reservado', 'total']) assert.equal(front.elementos.get(id).textContent, '—');
  assert.deepEqual(front.idsVisibles(), []);
});

test('REQ-23 debe_impedir_crear_pedido_cuando_el_producto_no_existe', async () => {
  const front = await preparar();
  front.elementos.get('id-producto').value = eliminado.productId;
  await front.accionar('form-pedido', 'submit');
  assert.equal(front.solicitudes.some(({ opciones }) => opciones.method === 'POST'), false);
  assert.match(front.elementos.get('aviso').textContent, /ya no existe/);
});

test('EXT-02 debe_convertir_fechas_y_conservar_el_rango_al_paginar', async () => {
  const front = await preparar({ responder: (url) => {
    if (url.pathname === '/api/orders') return Response.json({ items: [], page: Number(url.searchParams.get('page')), totalElements: 21, totalPages: 2 });
  } });
  front.elementos.get('fecha-desde').value = '2026-10-01'; front.elementos.get('fecha-hasta').value = '2026-10-10';
  await front.accionar('form-listado-pedidos', 'submit');
  await front.accionar('pedidos-siguiente');
  const ultima = front.solicitudes.at(-1).url;
  assert.equal(ultima.searchParams.get('from'), '10/01/2026');
  assert.equal(ultima.searchParams.get('to'), '10/10/2026');
  assert.equal(ultima.searchParams.get('page'), '1');
});

test('EXT-02 debe_rechazar_rangos_de_mas_de_31_dias_sin_enviar_consulta', async () => {
  const front = await preparar(); const cantidad = front.solicitudes.length;
  front.elementos.get('fecha-desde').value = '2026-10-01'; front.elementos.get('fecha-hasta').value = '2026-11-01';
  await front.accionar('form-listado-pedidos', 'submit');
  assert.equal(front.solicitudes.length, cantidad); assert.match(front.elementos.get('aviso').textContent, /31 días/);
});

test('EXT-02 debe_mostrar_pedidos_y_consultar_el_seleccionado', async () => {
  const front = await preparar({ responder: (url) => {
    if (url.pathname === '/api/orders') return Response.json({ items: [pedido], page: 0, totalElements: 1, totalPages: 1 });
    if (url.pathname === `/api/orders/${pedido.orderId}`) return Response.json(pedido);
  } });
  await front.accionar('form-listado-pedidos', 'submit');
  const fila = front.elementos.get('listado-pedidos').hijos[0];
  assert.equal(fila.hijos[0].textContent, pedido.orderId); assert.equal(fila.hijos[3].textContent, '2');
  front.elementos.get('id-pedido').value = pedido.orderId; await front.accionar('form-consulta-pedido', 'submit');
  assert.equal(front.elementos.get('estado').textContent, 'CONFIRMED');
  assert.equal(front.elementos.get('detalle-producto').textContent, producto.name);
  front.cerrar();
});

test('REQ-21 REQ-24 debe_cancelar_con_delete_y_reutilizar_la_clave_al_repetir', async () => {
  const cancelaciones = [];
  const front = await preparar({ responder: (url, opciones) => {
    if (opciones.method === 'DELETE' && url.pathname === `/api/orders/${pedido.orderId}`) {
      cancelaciones.push(opciones);
      return Response.json({ ...pedido, status: 'CANCELLED' });
    }
  } });
  front.elementos.get('id-pedido').value = pedido.orderId;
  await front.accionar('cancelar');
  assert.equal(front.elementos.get('estado').textContent, 'CANCELLED');
  await front.accionar('cancelar');
  assert.equal(cancelaciones.length, 2);
  assert.equal(cancelaciones[0].body, undefined);
  assert.ok(cancelaciones[0].headers['Idempotency-Key']);
  assert.equal(cancelaciones[0].headers['Idempotency-Key'], cancelaciones[1].headers['Idempotency-Key']);
  assert.equal(front.solicitudes.some(({ url }) => url.pathname.endsWith('/cancel')), false);
  front.cerrar();
});

test('EXT-03 REQ-24 debe_repetir_ajuste_con_misma_clave_cuerpo_y_un_solo_movimiento', async () => {
  const escrituras = []; const movimientos = [];
  const front = await preparar({ responder: (url, opciones) => {
    if (opciones.method === 'PUT' && url.pathname.endsWith('/stock')) {
      escrituras.push(opciones);
      if (escrituras.length === 1) movimientos.push({ movementId: crypto.randomUUID(), type: 'ADD', quantity: 3,
        reason: 'Reposición', createdAt: '2026-10-10T12:00:00Z', totalAfter: 8, availableAfter: 8 });
      return Response.json({ ...producto, movement: movimientos[0], total: 8, available: 8 }, { headers: { 'Idempotent-Replayed': String(escrituras.length > 1) } });
    }
    if (url.pathname.endsWith('/stock/movements')) return Response.json({ items: movimientos, page: 0, totalElements: movimientos.length, totalPages: 1 });
  } });
  front.elementos.get('id-producto').value = producto.productId; front.elementos.get('disponible-ajuste').value = '8';
  front.elementos.get('motivo-ajuste').value = 'Reposición'; await front.accionar('form-ajuste', 'submit');
  await front.accionar('repetir-ajuste');
  assert.equal(escrituras.length, 2); assert.equal(movimientos.length, 1);
  assert.equal(escrituras[0].headers['Idempotency-Key'], escrituras[1].headers['Idempotency-Key']);
  assert.equal(escrituras[0].body, escrituras[1].body);
  assert.equal(escrituras[0].method, 'PUT');
  assert.deepEqual(JSON.parse(escrituras[0].body), { available: 8, reason: 'Reposición' });
  assert.equal(front.elementos.get('movimientos').hijos[0].hijos[5].textContent, 'Reposición');
  assert.match(front.elementos.get('solicitud').textContent, /idempotent-replayed/);
});

test('EXT-03 debe_mostrar_error_de_tope_y_conservar_stock', async () => {
  const front = await preparar({ responder: (url, opciones) => {
    if (opciones.method === 'PUT' && url.pathname.endsWith('/stock')) return Response.json({ code: 'STOCK_LIMIT_EXCEEDED', detail: 'El total supera el límite' }, { status: 409 });
  } });
  front.elementos.get('id-producto').value = producto.productId; await front.accionar('form-stock', 'submit');
  front.elementos.get('disponible-ajuste').value = '10000000';
  await front.accionar('form-ajuste', 'submit');
  assert.match(front.elementos.get('aviso').textContent, /STOCK_LIMIT_EXCEEDED/);
  assert.equal(front.elementos.get('disponible').textContent, 5);
});

test('EXT-03 REQ-13 debe_fijar_disponibles_en_cero_sin_modificar_reservas', async () => {
  const actual = { ...producto, total: 7, reserved: 2 };
  const front = await preparar({ productos: [actual], responder: (url, opciones) => {
    if (url.pathname !== `/api/products/${producto.productId}/stock`) return;
    if (opciones.method === 'PUT') {
      assert.deepEqual(JSON.parse(opciones.body), { available: 0, reason: null });
      actual.available = 0; actual.total = 2;
      return Response.json({ ...actual, movement: { type: 'REMOVE', quantity: 5 } });
    }
    return Response.json(actual);
  } });
  front.elementos.get('id-producto').value = producto.productId;
  front.elementos.get('disponible-ajuste').value = '0';
  await front.accionar('form-ajuste', 'submit');
  assert.equal(front.elementos.get('disponible').textContent, 0);
  assert.equal(front.elementos.get('reservado').textContent, 2);
  assert.equal(front.elementos.get('total').textContent, 2);
});

test('EXT-03 debe_informar_que_no_hubo_movimiento_si_el_stock_ya_tenia_ese_valor', async () => {
  const front = await preparar({ responder: (url, opciones) => {
    if (opciones.method === 'PUT') return Response.json({ ...producto, movement: null });
  } });
  front.elementos.get('id-producto').value = producto.productId;
  await front.accionar('form-ajuste', 'submit');
  assert.match(front.elementos.get('aviso').textContent, /No se registró un movimiento/);
  assert.equal(front.elementos.get('disponible').textContent, 5);
});

test('EXT-03 debe_rechazar_stock_vacio_negativo_fraccionario_o_fuera_del_limite', async () => {
  const front = await preparar();
  front.elementos.get('id-producto').value = producto.productId;
  for (const valor of ['', '-1', '1.5', '10000001']) {
    front.elementos.get('disponible-ajuste').value = valor;
    await front.accionar('form-ajuste', 'submit');
    assert.match(front.elementos.get('aviso').textContent, /entero entre 0 y 10000000/);
  }
  assert.equal(front.solicitudes.some(({ opciones }) => opciones.method === 'PUT'), false);
});

test('REQ-22 debe_mostrar_el_conflicto_de_nombre_duplicado', async () => {
  const front = await preparar({ responder: (url, opciones) => {
    if (url.pathname === '/api/products' && opciones.method === 'POST') return Response.json({ code: 'PRODUCT_ALREADY_EXISTS', detail: `Ya existe el producto ${producto.productId}` }, { status: 409 });
  } });
  front.elementos.get('nombre').value = 'Teclado'; await front.accionar('form-producto', 'submit');
  assert.match(front.elementos.get('aviso').textContent, /PRODUCT_ALREADY_EXISTS/);
  assert.deepEqual(front.idsVisibles(), [producto.productId]);
});
