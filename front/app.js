const elemento = (id) => document.getElementById(id);
const ajustes = { intervaloSagaMs: 1000, esperaSagaMs: 30000, esperaStockMs: 1000, reintentosStock: 4, tamanioPagina: 20 };
const patronUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const paginaVacia = () => ({ items: [], page: 0, totalElements: 0, totalPages: 0 });
let paginaProductos = paginaVacia();
let paginaPedidos = paginaVacia();
let paginaMovimientos = paginaVacia();
let productoSeleccionado = null;
let idProductoMovimientos = '';
let ultimaCreacion = null;
let ultimoAjuste = null;
let ultimaCancelacion = null;
let generacionConsulta = 0;
let generacionCatalogo = 0;
let idCorrelacionPedido = '';
let ocupado = false;
let filtroPedidos = { desde: '', hasta: '' };

function nuevaCorrelacion() { return `front-${crypto.randomUUID()}`; }
function avisar(texto, error = false) {
  elemento('aviso').textContent = texto;
  elemento('aviso').classList.toggle('error', error);
}
function validarId(id, recurso) {
  if (!patronUuid.test(id)) throw new Error(`Ingresa un UUID válido para ${recurso}.`);
  return id;
}
function actualizarBotones() {
  for (const boton of document.querySelectorAll('main button:not(#comprobar)')) boton.disabled = ocupado;
  const tieneProducto = patronUuid.test(elemento('id-producto').value.trim());
  elemento('repetir').disabled = ocupado || !ultimaCreacion;
  elemento('repetir-ajuste').disabled = ocupado || !ultimoAjuste;
  elemento('cancelar').disabled = ocupado || !patronUuid.test(elemento('id-pedido').value.trim());
  for (const id of ['aplicar-ajuste', 'consultar-movimientos', 'consultar-producto']) elemento(id).disabled = ocupado || !tieneProducto;
  for (const [nombre, pagina] of [['productos', paginaProductos], ['pedidos', paginaPedidos], ['movimientos', paginaMovimientos]]) {
    elemento(`${nombre}-anterior`).disabled = ocupado || pagina.page <= 0;
    elemento(`${nombre}-siguiente`).disabled = ocupado || pagina.page + 1 >= pagina.totalPages;
  }
}
async function ejecutar(accion) {
  if (ocupado) return;
  ocupado = true; actualizarBotones();
  try { await accion(); } catch (error) { avisar(error.message, true); }
  finally { ocupado = false; actualizarBotones(); }
}
async function solicitar(ruta, { metodo = 'GET', cuerpo, clave, correlacion, mostrar = true } = {}) {
  const cabeceras = {};
  if (clave) cabeceras['Idempotency-Key'] = clave;
  if (correlacion) cabeceras['X-Correlation-Id'] = correlacion;
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';
  let respuesta;
  try {
    respuesta = await fetch(`/api${ruta}`, {
      method: metodo, headers: cabeceras, body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      cache: 'no-store', signal: AbortSignal.timeout(20000),
    });
  } catch { throw new Error('No se pudo conectar al front. Comprueba que siga encendido.'); }
  const texto = await respuesta.text();
  let datos;
  try { datos = JSON.parse(texto); } catch { datos = { detail: texto || 'Respuesta vacía del API.' }; }
  if (mostrar) {
    elemento('resultado-http').textContent = `HTTP ${respuesta.status} · ${metodo}`;
    elemento('resultado-http').classList.toggle('error', !respuesta.ok);
    elemento('solicitud').textContent = JSON.stringify({ metodo, ruta, cabeceras, cuerpo,
      cabecerasRespuesta: Object.fromEntries(['x-correlation-id', 'location', 'retry-after', 'idempotent-replayed']
        .filter((nombre) => respuesta.headers.has(nombre)).map((nombre) => [nombre, respuesta.headers.get(nombre)])),
    }, null, 2);
    elemento('respuesta').textContent = JSON.stringify(datos, null, 2);
  }
  if (!respuesta.ok) {
    const error = new Error(`HTTP ${respuesta.status}${datos.code ? ` (${datos.code})` : ''}: ${datos.detail || datos.title || 'Solicitud rechazada.'}`);
    error.estadoHttp = respuesta.status;
    throw error;
  }
  return datos;
}
function descripcionPagina(pagina) {
  return pagina.totalElements ? `Página ${pagina.page + 1} de ${pagina.totalPages} · ${pagina.totalElements} registros` : 'Sin resultados';
}
function mostrarProductos() {
  const id = elemento('id-producto').value.trim();
  const productos = [...paginaProductos.items];
  if (productoSeleccionado?.productId === id && !productos.some((producto) => producto.productId === id)) productos.unshift(productoSeleccionado);
  elemento('productos').replaceChildren(new Option('Selecciona un producto…', ''));
  for (const producto of productos) elemento('productos').add(new Option(`${producto.name} · ${producto.available} disponibles`, producto.productId));
  elemento('productos').value = id;
  elemento('pagina-productos').textContent = descripcionPagina(paginaProductos);
  actualizarBotones();
}
function limpiarStock() { for (const id of ['disponible', 'reservado', 'total']) elemento(id).textContent = '—'; }
function vaciarTabla(id, columnas, mensaje) {
  const fila = document.createElement('tr');
  const celda = document.createElement('td');
  celda.colSpan = columnas; celda.textContent = mensaje; fila.append(celda);
  elemento(id).replaceChildren(fila);
}
function limpiarMovimientos() {
  paginaMovimientos = paginaVacia(); idProductoMovimientos = '';
  vaciarTabla('movimientos', 6, 'Consulta los movimientos del producto seleccionado.');
  elemento('pagina-movimientos').textContent = 'Sin consultar';
}
function descartarProducto(id) {
  paginaProductos.items = paginaProductos.items.filter((producto) => producto.productId !== id);
  if (elemento('id-producto').value.trim() === id) {
    elemento('id-producto').value = ''; productoSeleccionado = null;
    elemento('detalle-producto').textContent = 'El producto ya no existe.';
    limpiarStock(); limpiarMovimientos();
  }
  if (ultimaCreacion?.cuerpo.lines.some((linea) => linea.productId === id)) ultimaCreacion = null;
  if (ultimoAjuste?.id === id) ultimoAjuste = null;
  mostrarProductos();
}
function mostrarStock(stock) {
  if (stock.productId !== elemento('id-producto').value.trim()) return;
  elemento('disponible').textContent = stock.available;
  elemento('reservado').textContent = stock.reserved;
  elemento('total').textContent = stock.total;
  if (productoSeleccionado?.productId === stock.productId) productoSeleccionado = { ...productoSeleccionado, ...stock };
  paginaProductos.items = paginaProductos.items.map((producto) => producto.productId === stock.productId ? { ...producto, ...stock } : producto);
  mostrarProductos();
}
function mostrarProducto(producto) {
  if (producto.productId !== elemento('id-producto').value.trim()) return;
  productoSeleccionado = producto;
  elemento('detalle-producto').textContent = producto.name;
  mostrarStock(producto);
}
async function cargarProductos(pagina = 0, mostrar = true) {
  const generacion = ++generacionCatalogo;
  try {
    const datos = await solicitar(`/products?page=${pagina}&size=${ajustes.tamanioPagina}`, { mostrar });
    if (generacion !== generacionCatalogo) return;
    if (datos.totalPages > 0 && pagina >= datos.totalPages) return cargarProductos(datos.totalPages - 1, mostrar);
    paginaProductos = datos;
    const seleccionado = datos.items.find((producto) => producto.productId === elemento('id-producto').value.trim());
    if (seleccionado) mostrarProducto(seleccionado);
    mostrarProductos();
  } catch (error) {
    if (generacion === generacionCatalogo) {
      paginaProductos = paginaVacia(); productoSeleccionado = null; limpiarStock(); mostrarProductos();
      elemento('detalle-producto').textContent = 'No se pudo actualizar el catálogo.';
      elemento('pagina-productos').textContent = 'Catálogo no disponible';
    }
    throw error;
  }
}
async function consultarProducto(mostrar = true) {
  const id = validarId(elemento('id-producto').value.trim(), 'el producto');
  try {
    const producto = await solicitar(`/products/${id}`, { mostrar });
    mostrarProducto(producto); return producto;
  } catch (error) {
    if (error.estadoHttp === 404) descartarProducto(id);
    throw error;
  }
}
async function consultarStock(mostrar = true) {
  const id = validarId(elemento('id-producto').value.trim(), 'el producto');
  try {
    const stock = await solicitar(`/products/${id}/stock`, { mostrar });
    mostrarStock(stock); return stock;
  } catch (error) {
    if (error.estadoHttp === 404) { descartarProducto(id); throw new Error('El producto ya no existe. Actualiza el catálogo.'); }
    throw error;
  }
}
function fechaLegible(fecha) {
  return new Date(fecha).toLocaleString('es-PY', { timeZone: 'America/Asuncion', dateStyle: 'short', timeStyle: 'short' });
}
function agregarFila(id, valores) {
  const fila = document.createElement('tr');
  for (const valor of valores) {
    const celda = document.createElement('td');
    if (typeof valor === 'object' && valor !== null) celda.append(valor);
    else celda.textContent = String(valor ?? '—');
    fila.append(celda);
  }
  elemento(id).append(fila);
}
function fechaApi(fecha) {
  if (!fecha) return '';
  const [anio, mes, dia] = fecha.split('-');
  return `${mes}/${dia}/${anio}`;
}
async function cargarPedidos(pagina = 0, mostrar = true) {
  const consulta = new URLSearchParams({ page: String(pagina), size: String(ajustes.tamanioPagina) });
  if (filtroPedidos.desde) consulta.set('from', fechaApi(filtroPedidos.desde));
  if (filtroPedidos.hasta) consulta.set('to', fechaApi(filtroPedidos.hasta));
  try {
    paginaPedidos = await solicitar(`/orders?${consulta}`, { mostrar });
    elemento('listado-pedidos').replaceChildren();
    for (const pedido of paginaPedidos.items) {
      const boton = document.createElement('button');
      boton.type = 'button'; boton.className = 'enlace'; boton.textContent = 'Ver pedido';
      boton.addEventListener('click', () => { void ejecutar(() => seleccionarPedido(pedido.orderId)); });
      agregarFila('listado-pedidos', [pedido.orderId, fechaLegible(pedido.createdAt), pedido.status,
        pedido.lines.reduce((total, linea) => total + linea.quantity, 0), boton]);
    }
    if (!paginaPedidos.items.length) vaciarTabla('listado-pedidos', 5, 'No hay pedidos en este rango.');
    elemento('pagina-pedidos').textContent = descripcionPagina(paginaPedidos);
  } catch (error) {
    paginaPedidos = paginaVacia(); vaciarTabla('listado-pedidos', 5, 'No se pudo cargar el listado.');
    elemento('pagina-pedidos').textContent = 'Listado no disponible'; throw error;
  } finally { actualizarBotones(); }
}
async function cargarMovimientos(pagina = 0, mostrar = true) {
  const id = validarId(elemento('id-producto').value.trim(), 'el producto');
  try {
    const datos = await solicitar(`/products/${id}/stock/movements?page=${pagina}&size=${ajustes.tamanioPagina}`, { mostrar });
    if (id !== elemento('id-producto').value.trim()) return;
    paginaMovimientos = datos; idProductoMovimientos = id;
    elemento('movimientos').replaceChildren();
    for (const movimiento of datos.items) agregarFila('movimientos', [fechaLegible(movimiento.createdAt),
      movimiento.type === 'ADD' ? 'Agregado' : 'Retiro', movimiento.quantity, movimiento.availableAfter, movimiento.totalAfter, movimiento.reason]);
    if (!datos.items.length) vaciarTabla('movimientos', 6, 'No hay ajustes registrados para este producto.');
    elemento('pagina-movimientos').textContent = descripcionPagina(datos);
  } catch (error) {
    if (id === elemento('id-producto').value.trim()) limpiarMovimientos();
    if (error.estadoHttp === 404) descartarProducto(id);
    throw error;
  } finally { actualizarBotones(); }
}
function mostrarPedido(pedido) {
  if (pedido.orderId !== elemento('id-pedido').value.trim()) return;
  elemento('estado').textContent = pedido.status; elemento('estado').className = `estado ${pedido.status}`;
  const descripciones = { PENDING: 'Esperando inventario…', CONFIRMED: 'Stock reservado.',
    REJECTED: `Pedido rechazado: ${pedido.rejectionReason || 'consulta la respuesta'}.`, CANCELLED: 'Cancelado. Actualizando stock…' };
  elemento('detalle-estado').textContent = descripciones[pedido.status] || 'Consulta la respuesta del API.';
}
function esperar(ms) { return new Promise((resolver) => setTimeout(resolver, ms)); }
async function refrescarStockEventual(generacion) {
  for (let intento = 0; intento < ajustes.reintentosStock; intento++) {
    if (generacion !== generacionConsulta) return;
    if (patronUuid.test(elemento('id-producto').value.trim())) {
      try { await consultarStock(false); } catch (error) { avisar(`No se pudo actualizar el stock: ${error.message}`, true); return; }
    }
    await esperar(ajustes.esperaStockMs);
  }
  if (generacion === generacionConsulta) await refrescarLecturas([() => cargarProductos(paginaProductos.page, false), () => cargarPedidos(paginaPedidos.page, false)]);
}
async function seguirPedido(id, generacion) {
  const limite = Date.now() + ajustes.esperaSagaMs;
  while (generacion === generacionConsulta && elemento('id-pedido').value.trim() === id) {
    await esperar(ajustes.intervaloSagaMs);
    if (generacion !== generacionConsulta || elemento('id-pedido').value.trim() !== id) return;
    try {
      const pedido = await solicitar(`/orders/${id}`, { mostrar: false });
      if (generacion !== generacionConsulta) return;
      mostrarPedido(pedido);
      if (pedido.status !== 'PENDING') {
        avisar(`Estado del pedido: ${pedido.status}.`); await refrescarStockEventual(generacion); return;
      }
    } catch (error) { avisar(`Se interrumpió la actualización: ${error.message}`, true); return; }
    if (Date.now() >= limite) { avisar('El pedido sigue PENDING. Usa Consultar pedido para comprobarlo.'); return; }
  }
}
function observarPedido(pedido) {
  const generacion = ++generacionConsulta;
  mostrarPedido(pedido);
  if (pedido.status === 'PENDING') void seguirPedido(pedido.orderId, generacion);
  else void refrescarStockEventual(generacion);
}
async function refrescarLecturas(acciones) {
  const resultados = await Promise.allSettled(acciones.map((accion) => accion()));
  const fallo = resultados.find((resultado) => resultado.status === 'rejected');
  if (fallo) avisar(`La operación terminó, pero faltó actualizar una consulta: ${fallo.reason.message}`, true);
}
async function seleccionarPedido(id) {
  ++generacionConsulta;
  const pedido = await solicitar(`/orders/${validarId(id, 'el pedido')}`);
  elemento('id-pedido').value = pedido.orderId;
  idCorrelacionPedido = ultimaCreacion?.idPedido === id ? ultimaCreacion.correlacion : '';
  ultimaCancelacion = null;
  if (pedido.lines.length === 1) {
    elemento('id-producto').value = pedido.lines[0].productId; limpiarMovimientos();
    await refrescarLecturas([() => consultarProducto(false)]);
  }
  observarPedido(pedido); avisar(`Estado del pedido: ${pedido.status}.`);
}
async function crearPedido(solicitud) {
  ++generacionConsulta;
  const pedido = await solicitar('/orders', solicitud);
  solicitud.idPedido = pedido.orderId; elemento('id-pedido').value = pedido.orderId;
  idCorrelacionPedido = solicitud.correlacion; ultimaCancelacion = null;
  observarPedido(pedido); avisar(`Pedido recibido: ${pedido.status}.`);
  await refrescarLecturas([() => cargarPedidos(0, false)]);
}
async function aplicarAjuste(ajuste) {
  const stock = await solicitar(`/products/${ajuste.id}/stock`, ajuste);
  mostrarStock(stock);
  avisar(stock.movement ? 'Solicitud de stock procesada.' : 'El stock ya tenía ese valor. No se registró un movimiento.');
  if (ajuste.id === elemento('id-producto').value.trim()) {
    await refrescarLecturas([() => consultarStock(false), () => cargarMovimientos(0, false), () => cargarProductos(paginaProductos.page, false)]);
  }
}
elemento('form-producto').addEventListener('submit', (evento) => {
  evento.preventDefault();
  void ejecutar(async () => {
    const producto = await solicitar('/products', { metodo: 'POST', clave: crypto.randomUUID(), correlacion: nuevaCorrelacion(),
      cuerpo: { name: elemento('nombre').value.trim(), initialQuantity: Number(elemento('stock-inicial').value) } });
    elemento('id-producto').value = producto.productId; limpiarMovimientos(); mostrarProducto(producto);
    avisar('Producto creado.'); await refrescarLecturas([() => cargarProductos(0, false)]);
  });
});
elemento('productos').addEventListener('change', () => {
  elemento('id-producto').value = elemento('productos').value;
  productoSeleccionado = null; limpiarStock(); limpiarMovimientos(); actualizarBotones();
  elemento('detalle-producto').textContent = 'Selecciona un producto del catálogo o consulta su ID.';
  if (elemento('id-producto').value) void ejecutar(() => consultarProducto());
});
elemento('id-producto').addEventListener('input', () => {
  productoSeleccionado = null; limpiarStock(); limpiarMovimientos();
  elemento('detalle-producto').textContent = 'Consulta el ID para ver el producto.'; mostrarProductos();
});
elemento('actualizar-productos').addEventListener('click', () => {
  void ejecutar(async () => {
    await cargarProductos(paginaProductos.page);
    if (patronUuid.test(elemento('id-producto').value.trim())) await consultarProducto(false);
    avisar('Catálogo actualizado.');
  });
});
elemento('consultar-producto').addEventListener('click', () => { void ejecutar(() => consultarProducto()); });
elemento('form-stock').addEventListener('submit', (evento) => { evento.preventDefault(); void ejecutar(() => consultarStock()); });
elemento('form-pedido').addEventListener('submit', (evento) => {
  evento.preventDefault();
  void ejecutar(async () => {
    const id = validarId(elemento('id-producto').value.trim(), 'el producto'); await consultarStock(false);
    ultimaCreacion = { metodo: 'POST', clave: crypto.randomUUID(), correlacion: elemento('correlacion').value.trim(),
      cuerpo: { lines: [{ productId: id, quantity: Number(elemento('cantidad').value) }] } };
    await crearPedido(ultimaCreacion); elemento('correlacion').value = nuevaCorrelacion();
  });
});
elemento('repetir').addEventListener('click', () => { void ejecutar(() => crearPedido(ultimaCreacion)); });
elemento('id-pedido').addEventListener('input', () => {
  ++generacionConsulta; idCorrelacionPedido = '';
  elemento('estado').textContent = 'SIN CONSULTAR'; elemento('estado').className = 'estado';
  elemento('detalle-estado').textContent = 'Pendiente de consulta.'; actualizarBotones();
});
elemento('form-consulta-pedido').addEventListener('submit', (evento) => {
  evento.preventDefault(); void ejecutar(() => seleccionarPedido(elemento('id-pedido').value.trim()));
});
elemento('cancelar').addEventListener('click', () => {
  void ejecutar(async () => {
    const id = validarId(elemento('id-pedido').value.trim(), 'el pedido'); ++generacionConsulta;
    if (ultimaCancelacion?.id !== id) ultimaCancelacion = { id, clave: crypto.randomUUID(), correlacion: idCorrelacionPedido || nuevaCorrelacion() };
    const pedido = await solicitar(`/orders/${id}`, { metodo: 'DELETE', clave: ultimaCancelacion.clave, correlacion: ultimaCancelacion.correlacion });
    idCorrelacionPedido = ultimaCancelacion.correlacion; observarPedido(pedido); avisar('Pedido cancelado. Actualizando stock…');
  });
});
elemento('form-ajuste').addEventListener('submit', (evento) => {
  evento.preventDefault();
  void ejecutar(async () => {
    const id = validarId(elemento('id-producto').value.trim(), 'el producto');
    const valor = elemento('disponible-ajuste').value.trim();
    const disponible = Number(valor);
    if (!valor || !Number.isInteger(disponible) || disponible < 0 || disponible > 10000000) throw new Error('Ingresa un stock disponible entero entre 0 y 10000000.');
    ultimoAjuste = { id, metodo: 'PUT', clave: crypto.randomUUID(), correlacion: nuevaCorrelacion(),
      cuerpo: { available: disponible, reason: elemento('motivo-ajuste').value.trim() || null } };
    await aplicarAjuste(ultimoAjuste);
  });
});
elemento('repetir-ajuste').addEventListener('click', () => { void ejecutar(() => aplicarAjuste(ultimoAjuste)); });
elemento('consultar-movimientos').addEventListener('click', () => { void ejecutar(() => cargarMovimientos()); });
elemento('form-listado-pedidos').addEventListener('submit', (evento) => {
  evento.preventDefault();
  void ejecutar(async () => {
    const desde = elemento('fecha-desde').value; const hasta = elemento('fecha-hasta').value;
    if (desde && hasta) {
      const dias = (Date.parse(hasta) - Date.parse(desde)) / 86400000 + 1;
      if (dias < 1 || dias > 31) throw new Error('El rango debe estar ordenado y abarcar como máximo 31 días.');
    }
    filtroPedidos = { desde, hasta }; await cargarPedidos(0);
  });
});
for (const [nombre, cargar, obtenerPagina] of [
  ['productos', cargarProductos, () => paginaProductos.page], ['pedidos', cargarPedidos, () => paginaPedidos.page],
  ['movimientos', cargarMovimientos, () => paginaMovimientos.page],
]) {
  for (const [direccion, delta] of [['anterior', -1], ['siguiente', 1]]) elemento(`${nombre}-${direccion}`).addEventListener('click', () => {
    if (nombre === 'movimientos' && idProductoMovimientos !== elemento('id-producto').value.trim()) return;
    void ejecutar(() => cargar(obtenerPagina() + delta));
  });
}
elemento('copiar-filtro').addEventListener('click', () => {
  void ejecutar(async () => {
    const id = elemento('id-pedido').value.trim();
    if (!idCorrelacionPedido && !patronUuid.test(id)) { avisar('Crea o selecciona un pedido para copiar su filtro.'); return; }
    const filtro = idCorrelacionPedido ? `correlationId = '${idCorrelacionPedido.replaceAll("'", "''")}'` : `orderId = '${id}'`;
    try { await navigator.clipboard.writeText(filtro); avisar(`Filtro copiado: ${filtro}`); }
    catch { avisar(`Filtro para Seq: ${filtro}`); }
  });
});
async function comprobarConexion() {
  elemento('comprobar').disabled = true;
  try {
    const salud = await solicitar('/actuator/health', { mostrar: false });
    if (salud.status !== 'UP') throw new Error('Gateway sin disponibilidad.');
    elemento('conexion').textContent = 'Gateway conectado'; elemento('punto-conexion').className = 'punto ok';
  } catch {
    elemento('conexion').textContent = 'Gateway sin conexión'; elemento('punto-conexion').className = 'punto error';
    avisar('No se pudo comprobar el gateway en el puerto 8080.', true);
  } finally { elemento('comprobar').disabled = false; }
}
elemento('comprobar').addEventListener('click', () => { void comprobarConexion(); });
elemento('correlacion').value = nuevaCorrelacion();
mostrarProductos(); actualizarBotones(); void comprobarConexion();
void ejecutar(async () => {
  const resultados = await Promise.allSettled([cargarProductos(0, false), cargarPedidos(0, false)]);
  const fallo = resultados.find((resultado) => resultado.status === 'rejected');
  if (fallo) avisar(`No se pudo cargar un listado: ${fallo.reason.message}`, true);
});
