package com.roshka.challenge.inventario.dominio;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reserva de stock de un pedido: como máximo una por pedido. Solo la transición RESERVED a RELEASED
 * devuelve stock, así que el stock se devuelve como máximo una vez. Una reserva RELEASED sin líneas
 * es un tombstone: se liberó antes de haberse reservado y rechaza cualquier reserva posterior.
 */
public final class Reserva {

  private final UUID idPedido;
  private final List<LineaReserva> lineas;
  private final MotivoRechazo motivoRechazo;
  private final List<UUID> idsProductoRechazados;
  private EstadoReserva estado;

  private Reserva(
      UUID idPedido,
      EstadoReserva estado,
      List<LineaReserva> lineas,
      MotivoRechazo motivoRechazo,
      List<UUID> idsProductoRechazados) {
    this.idPedido = Objects.requireNonNull(idPedido, "idPedido");
    this.estado = Objects.requireNonNull(estado, "estado");
    this.lineas = enOrdenDeBloqueo(lineas);
    this.motivoRechazo = motivoRechazo;
    this.idsProductoRechazados = List.copyOf(idsProductoRechazados);
  }

  public static Reserva reservado(UUID idPedido, List<LineaReserva> lineas) {
    return new Reserva(idPedido, EstadoReserva.RESERVED, lineas, null, List.of());
  }

  public static Reserva rechazado(
      UUID idPedido, List<LineaReserva> lineas, MotivoRechazo motivo, List<UUID> idsProducto) {
    return new Reserva(
        idPedido, EstadoReserva.REJECTED, lineas, Objects.requireNonNull(motivo), idsProducto);
  }

  public static Reserva tombstone(UUID idPedido) {
    return new Reserva(idPedido, EstadoReserva.RELEASED, List.of(), null, List.of());
  }

  public static Reserva restaurar(
      UUID idPedido,
      EstadoReserva estado,
      List<LineaReserva> lineas,
      MotivoRechazo motivoRechazo,
      List<UUID> idsProductoRechazados) {
    return new Reserva(idPedido, estado, lineas, motivoRechazo, idsProductoRechazados);
  }

  /**
   * idProducto ascendente: toda transacción bloquea las filas de productos en el mismo orden (ADR
   * 0009).
   */
  public static List<LineaReserva> enOrdenDeBloqueo(List<LineaReserva> lineas) {
    return lineas.stream().sorted(Comparator.comparing(LineaReserva::idProducto)).toList();
  }

  public RespuestaReserva respuestaAReservar() {
    return switch (estado) {
      case RESERVED -> new RespuestaReserva.Confirmado(lineas);
      case REJECTED -> new RespuestaReserva.Rechazado(motivoRechazo, idsProductoRechazados);
      case RELEASED ->
          new RespuestaReserva.Rechazado(MotivoRechazo.RESERVATION_RELEASED, List.of());
    };
  }

  public ResultadoLiberacion liberar() {
    if (estado != EstadoReserva.RESERVED) {
      return ResultadoLiberacion.NADA_QUE_LIBERAR;
    }
    estado = EstadoReserva.RELEASED;
    return ResultadoLiberacion.DEVUELTO;
  }

  public UUID idPedido() {
    return idPedido;
  }

  public EstadoReserva estado() {
    return estado;
  }

  public List<LineaReserva> lineas() {
    return lineas;
  }

  public MotivoRechazo motivoRechazo() {
    return motivoRechazo;
  }

  public List<UUID> idsProductoRechazados() {
    return idsProductoRechazados;
  }
}
