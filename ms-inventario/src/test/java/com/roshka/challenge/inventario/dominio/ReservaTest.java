package com.roshka.challenge.inventario.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservaTest {

  private static final UUID PEDIDO = UUID.randomUUID();
  private static final UUID BAJO = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID ALTO = UUID.fromString("7fffffff-0000-0000-0000-000000000001");

  private final List<LineaReserva> lineas =
      List.of(new LineaReserva(ALTO, 2), new LineaReserva(BAJO, 3));

  @Test
  @DisplayName("REQ-11 · 001 RN-05 las líneas se procesan en orden ascendente de idProducto")
  void debe_ordenar_las_lineas_por_id_de_producto_al_preparar_el_orden_de_bloqueo() {
    assertThat(Reserva.enOrdenDeBloqueo(lineas))
        .extracting(LineaReserva::idProducto)
        .containsExactly(BAJO, ALTO);
  }

  @Test
  @DisplayName(
      "REQ-11 · 004 tabla: RESERVED responde con una confirmación a un ReservarStock repetido")
  void debe_confirmar_de_nuevo_cuando_una_reserva_reservada_recibe_reservar() {
    var reserva = Reserva.reservado(PEDIDO, lineas);

    assertThat(reserva.respuestaAReservar())
        .isEqualTo(new RespuestaReserva.Confirmado(Reserva.enOrdenDeBloqueo(lineas)));
  }

  @Test
  @DisplayName("REQ-11 · 004 tabla: REJECTED responde con su motivo original")
  void debe_rechazar_con_el_motivo_original_cuando_una_reserva_rechazada_recibe_reservar() {
    var reserva =
        Reserva.rechazado(PEDIDO, lineas, MotivoRechazo.INSUFFICIENT_STOCK, List.of(BAJO));

    assertThat(reserva.respuestaAReservar())
        .isEqualTo(new RespuestaReserva.Rechazado(MotivoRechazo.INSUFFICIENT_STOCK, List.of(BAJO)));
  }

  @Test
  @DisplayName("REQ-27 · 004-AC-05 un tombstone rechaza cualquier ReservarStock posterior")
  void debe_rechazar_como_liberado_cuando_un_tombstone_recibe_reservar() {
    var tombstone = Reserva.tombstone(PEDIDO);

    assertThat(tombstone.estado()).isEqualTo(EstadoReserva.RELEASED);
    assertThat(tombstone.lineas()).isEmpty();
    assertThat(tombstone.respuestaAReservar())
        .isEqualTo(new RespuestaReserva.Rechazado(MotivoRechazo.RESERVATION_RELEASED, List.of()));
  }

  @Test
  @DisplayName("REQ-12 · 004 tabla: liberar RESERVED devuelve su stock y pasa a RELEASED")
  void debe_devolver_el_stock_cuando_una_reserva_reservada_se_libera() {
    var reserva = Reserva.reservado(PEDIDO, lineas);

    assertThat(reserva.liberar()).isEqualTo(ResultadoLiberacion.DEVUELTO);
    assertThat(reserva.estado()).isEqualTo(EstadoReserva.RELEASED);
  }

  @Test
  @DisplayName("REQ-12 · 004-AC-11 el stock se devuelve como máximo una vez")
  void debe_no_liberar_nada_cuando_la_reserva_ya_fue_liberada() {
    var reserva = Reserva.reservado(PEDIDO, lineas);
    reserva.liberar();

    assertThat(reserva.liberar()).isEqualTo(ResultadoLiberacion.NADA_QUE_LIBERAR);
    assertThat(reserva.estado()).isEqualTo(EstadoReserva.RELEASED);
    assertThat(reserva.respuestaAReservar())
        .isEqualTo(new RespuestaReserva.Rechazado(MotivoRechazo.RESERVATION_RELEASED, List.of()));
  }

  @Test
  @DisplayName("REQ-12 · 004 tabla: liberar REJECTED no cambia nada")
  void debe_no_liberar_nada_cuando_la_reserva_fue_rechazada() {
    var reserva = Reserva.rechazado(PEDIDO, lineas, MotivoRechazo.UNKNOWN_PRODUCT, List.of(ALTO));

    assertThat(reserva.liberar()).isEqualTo(ResultadoLiberacion.NADA_QUE_LIBERAR);
    assertThat(reserva.estado()).isEqualTo(EstadoReserva.REJECTED);
  }
}
