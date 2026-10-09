package com.roshka.challenge.inventario.aplicacion;

import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.inventario.aplicacion.consulta.Pagina;
import com.roshka.challenge.inventario.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.LibroStock;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.MensajesProcesados;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.MetricasInventario;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.MovimientosStock;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.OutboxMensajes;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ProductoRepository;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ReservaRepository;
import com.roshka.challenge.inventario.dominio.LineaReserva;
import com.roshka.challenge.inventario.dominio.MotivoRechazo;
import com.roshka.challenge.inventario.dominio.MovimientoStock;
import com.roshka.challenge.inventario.dominio.Producto;
import com.roshka.challenge.inventario.dominio.ProductoDuplicado;
import com.roshka.challenge.inventario.dominio.Reserva;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementación en memoria de todos los puertos de salida, con la misma semántica que la base de
 * datos.
 */
class InventarioEnMemoria
    implements ProductoRepository,
        ReservaRepository,
        LibroStock,
        MovimientosStock,
        MensajesProcesados,
        OutboxMensajes,
        MetricasInventario {

  record Publicado(String topico, SobreMensaje<?> sobre) {}

  final Map<UUID, Producto> productos = new HashMap<>();
  final List<MovimientoStock> movimientosGuardados = new ArrayList<>();
  final Map<UUID, Reserva> reservas = new HashMap<>();
  final Set<UUID> procesado = new HashSet<>();
  final List<Publicado> outbox = new ArrayList<>();
  final List<List<LineaReserva>> lotesLiberados = new ArrayList<>();

  Producto stock(UUID idProducto) {
    return productos.get(idProducto);
  }

  @Override
  public void insertar(Producto producto) {
    buscarPorNombre(producto.nombre())
        .ifPresent(
            existente -> {
              throw new ProductoDuplicado(producto.nombre(), null);
            });
    productos.put(producto.id(), producto);
  }

  @Override
  public void insertar(MovimientoStock movimiento) {
    movimientosGuardados.add(movimiento);
  }

  @Override
  public Pagina<MovimientoStock> listar(UUID idProducto, SolicitudPagina solicitud) {
    var ordenados =
        movimientosGuardados.stream()
            .filter(m -> m.idProducto().equals(idProducto))
            .sorted(
                Comparator.comparing(MovimientoStock::creadoEn)
                    .thenComparing(MovimientoStock::id)
                    .reversed())
            .toList();
    var primero = (int) Math.min(solicitud.desplazamiento(), ordenados.size());
    var ultimo = Math.min(primero + solicitud.tamanio(), ordenados.size());
    return new Pagina<>(
        ordenados.subList(primero, ultimo),
        solicitud.pagina(),
        solicitud.tamanio(),
        ordenados.size());
  }

  @Override
  public Pagina<Producto> listar(SolicitudPagina solicitud) {
    var ordenados =
        productos.values().stream()
            .sorted(
                Comparator.comparing((Producto p) -> p.nombre().strip().toLowerCase())
                    .thenComparing(Producto::id))
            .toList();
    var desde = (int) Math.min(solicitud.desplazamiento(), ordenados.size());
    var hasta = Math.min(desde + solicitud.tamanio(), ordenados.size());
    return new Pagina<>(
        ordenados.subList(desde, hasta), solicitud.pagina(), solicitud.tamanio(), ordenados.size());
  }

  @Override
  public Optional<Producto> bloquearPorId(UUID idProducto) {
    return buscarPorId(idProducto);
  }

  @Override
  public void actualizarStock(Producto producto) {
    productos.put(producto.id(), producto);
  }

  @Override
  public Optional<Producto> buscarPorNombre(String nombreNormalizado) {
    return productos.values().stream()
        .filter(p -> p.nombre().strip().equalsIgnoreCase(nombreNormalizado.strip()))
        .findFirst();
  }

  @Override
  public Optional<Producto> buscarPorId(UUID idProducto) {
    return Optional.ofNullable(productos.get(idProducto));
  }

  @Override
  public Optional<Reserva> buscarParaActualizar(UUID idPedido) {
    return Optional.ofNullable(reservas.get(idPedido));
  }

  @Override
  public void insertar(Reserva reserva) {
    if (reservas.putIfAbsent(reserva.idPedido(), reserva) != null) {
      throw new IllegalStateException("clave duplicada: reserva " + reserva.idPedido());
    }
  }

  @Override
  public void actualizarEstado(Reserva reserva) {
    reservas.put(reserva.idPedido(), reserva);
  }

  @Override
  public ResultadoLibro reservar(List<LineaReserva> lineasEnOrdenDeBloqueo) {
    var desconocido =
        lineasEnOrdenDeBloqueo.stream()
            .map(LineaReserva::idProducto)
            .filter(id -> !productos.containsKey(id))
            .toList();
    if (!desconocido.isEmpty()) {
      return new Rechazado(
          MotivoRechazo.UNKNOWN_PRODUCT,
          desconocido,
          lineasEnOrdenDeBloqueo.stream()
              .filter(linea -> desconocido.contains(linea.idProducto()))
              .map(linea -> new Faltante(linea.idProducto(), linea.cantidad(), null))
              .toList());
    }
    var insuficiente =
        lineasEnOrdenDeBloqueo.stream()
            .filter(linea -> productos.get(linea.idProducto()).disponible() < linea.cantidad())
            .map(
                linea ->
                    new Faltante(
                        linea.idProducto(),
                        linea.cantidad(),
                        productos.get(linea.idProducto()).disponible()))
            .toList();
    if (!insuficiente.isEmpty()) {
      return new Rechazado(
          MotivoRechazo.INSUFFICIENT_STOCK,
          insuficiente.stream().map(Faltante::idProducto).toList(),
          insuficiente);
    }
    lineasEnOrdenDeBloqueo.forEach(linea -> mover(linea.idProducto(), -linea.cantidad()));
    return new Aplicado();
  }

  @Override
  public void liberar(List<LineaReserva> lineasEnOrdenDeBloqueo) {
    lotesLiberados.add(lineasEnOrdenDeBloqueo);
    lineasEnOrdenDeBloqueo.forEach(linea -> mover(linea.idProducto(), linea.cantidad()));
  }

  private void mover(UUID idProducto, int aDisponible) {
    var p = productos.get(idProducto);
    productos.put(
        idProducto,
        new Producto(
            p.id(),
            p.nombre(),
            p.total(),
            p.disponible() + aDisponible,
            p.reservado() - aDisponible));
  }

  @Override
  public boolean marcarProcesado(UUID idMensaje) {
    return procesado.add(idMensaje);
  }

  @Override
  public void agregar(String topico, SobreMensaje<?> sobre) {
    outbox.add(new Publicado(topico, sobre));
  }

  @Override
  public void reservaDecidida(MotivoRechazo rechazo) {}
}
