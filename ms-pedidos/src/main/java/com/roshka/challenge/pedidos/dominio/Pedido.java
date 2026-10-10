package com.roshka.challenge.pedidos.dominio;

import com.roshka.challenge.pedidos.dominio.EventoPedido.LiberacionStockSolicitada;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoCancelado;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoColocado;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoConfirmado;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoRechazado;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Pedido. Las transiciones las decide {@link EstadoPedido}; el agregado las aplica y
 * registra eventos de dominio que el orquestador de la saga convierte en comandos para inventario.
 */
public final class Pedido {

  public static final int MAXIMO_LINEAS = 50;
  public static final int CANTIDAD_MAXIMA = 1000;

  private final UUID id;
  private final List<LineaPedido> lineas;
  private final String idCorrelacion;
  private final Instant creadoEn;
  private final Instant sagaIniciadaEn;
  private final List<EventoPedido> eventos = new ArrayList<>();
  private EstadoPedido estado;
  private MotivoRechazoPedido motivoRechazo;
  private Instant actualizadoEn;

  private Pedido(
      UUID id,
      EstadoPedido estado,
      MotivoRechazoPedido motivoRechazo,
      List<LineaPedido> lineas,
      String idCorrelacion,
      Instant creadoEn,
      Instant actualizadoEn,
      Instant sagaIniciadaEn) {
    this.id = Objects.requireNonNull(id, "id");
    this.estado = Objects.requireNonNull(estado, "estado");
    this.motivoRechazo = motivoRechazo;
    this.lineas = List.copyOf(lineas);
    this.idCorrelacion = idCorrelacion;
    this.creadoEn = creadoEn;
    this.actualizadoEn = actualizadoEn;
    this.sagaIniciadaEn = sagaIniciadaEn;
  }

  public static Pedido colocar(
      UUID id, List<LineaPedido> lineas, String idCorrelacion, Instant ahora) {
    validar(lineas);
    var pedido =
        new Pedido(id, EstadoPedido.PENDING, null, lineas, idCorrelacion, ahora, ahora, ahora);
    pedido.eventos.add(new PedidoColocado(id, pedido.lineas));
    return pedido;
  }

  public static Pedido restaurar(
      UUID id,
      EstadoPedido estado,
      MotivoRechazoPedido motivoRechazo,
      List<LineaPedido> lineas,
      String idCorrelacion,
      Instant creadoEn,
      Instant actualizadoEn,
      Instant sagaIniciadaEn) {
    return new Pedido(
        id, estado, motivoRechazo, lineas, idCorrelacion, creadoEn, actualizadoEn, sagaIniciadaEn);
  }

  public void cancelar(Instant ahora) {
    aplicar(estado.alCancelar(), null, ahora);
  }

  public void confirmarReserva(Instant ahora) {
    aplicar(estado.alStockReservado(), null, ahora);
  }

  public void rechazarReserva(MotivoRechazoPedido motivo, Instant ahora) {
    aplicar(estado.alStockRechazado(), motivo, ahora);
  }

  public void expirar(Instant ahora) {
    aplicar(estado.alTimeout(), MotivoRechazoPedido.TIMEOUT, ahora);
  }

  /** Devuelve los eventos registrados desde la última llamada y los olvida. */
  public List<EventoPedido> extraerEventos() {
    var extraido = List.copyOf(eventos);
    eventos.clear();
    return extraido;
  }

  private void aplicar(Transicion transicion, MotivoRechazoPedido motivo, Instant ahora) {
    if (transicion.objetivo() != estado) {
      estado = transicion.objetivo();
      actualizadoEn = ahora;
      eventos.add(
          switch (estado) {
            case CONFIRMED -> new PedidoConfirmado(id);
            case REJECTED -> {
              motivoRechazo = Objects.requireNonNull(motivo, "rejection reason");
              yield new PedidoRechazado(id, motivo);
            }
            case CANCELLED -> new PedidoCancelado(id);
            case PENDING -> throw new IllegalStateException("Ninguna transición vuelve a PENDING");
          });
    }
    if (transicion.liberar() != null) {
      eventos.add(new LiberacionStockSolicitada(id, transicion.liberar()));
    }
  }

  private static void validar(List<LineaPedido> lineas) {
    if (lineas == null || lineas.isEmpty() || lineas.size() > MAXIMO_LINEAS) {
      throw new PedidoInvalido("Un pedido debe tener entre 1 y " + MAXIMO_LINEAS + " líneas");
    }
    var productos = new HashSet<UUID>();
    for (var linea : lineas) {
      if (linea.cantidad() < 1 || linea.cantidad() > CANTIDAD_MAXIMA) {
        throw new PedidoInvalido("cantidad debe estar entre 1 y " + CANTIDAD_MAXIMA);
      }
      if (!productos.add(linea.idProducto())) {
        throw new LineaProductoDuplicada(linea.idProducto());
      }
    }
  }

  public UUID id() {
    return id;
  }

  public EstadoPedido estado() {
    return estado;
  }

  public MotivoRechazoPedido motivoRechazo() {
    return motivoRechazo;
  }

  public List<LineaPedido> lineas() {
    return lineas;
  }

  public String idCorrelacion() {
    return idCorrelacion;
  }

  public Instant creadoEn() {
    return creadoEn;
  }

  public Instant actualizadoEn() {
    return actualizadoEn;
  }

  public Instant sagaIniciadaEn() {
    return sagaIniciadaEn;
  }
}
