package com.roshka.challenge.pedidos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.pedidos.dominio.EventoPedido.LiberacionStockSolicitada;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoCancelado;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoColocado;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoConfirmado;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoRechazado;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PedidoTest {

  private static final Instant COLOCADO_EN = Instant.parse("2026-10-09T12:00:00Z");
  private static final Instant LATER = COLOCADO_EN.plusSeconds(5);

  private final UUID idPedido = UUID.randomUUID();
  private final List<LineaPedido> lineas = List.of(new LineaPedido(UUID.randomUUID(), 2));

  @Test
  @DisplayName(
      "REQ-03 REQ-06 · 002-AC-01 un pedido colocado queda PENDING y registra PedidoColocado")
  void debe_iniciar_pendiente_y_registrar_la_colocacion_cuando_se_coloca() {
    var pedido = Pedido.colocar(idPedido, lineas, "corr-1", COLOCADO_EN);

    assertThat(pedido.estado()).isEqualTo(EstadoPedido.PENDING);
    assertThat(pedido.sagaIniciadaEn()).isEqualTo(COLOCADO_EN);
    assertThat(pedido.idCorrelacion()).isEqualTo("corr-1");
    assertThat(pedido.extraerEventos()).containsExactly(new PedidoColocado(idPedido, lineas));
    assertThat(pedido.extraerEventos()).isEmpty();
  }

  @Test
  @DisplayName("REQ-19 · 002-AC-06 un pedido necesita al menos una línea")
  void debe_rechazar_el_pedido_cuando_no_tiene_lineas() {
    assertThatThrownBy(() -> Pedido.colocar(idPedido, List.of(), "c", COLOCADO_EN))
        .isInstanceOf(PedidoInvalido.class)
        .hasMessageContaining("líneas");
  }

  @Test
  @DisplayName("REQ-19 · 002-AC-06 un pedido tiene como máximo 50 líneas")
  void debe_rechazar_el_pedido_cuando_tiene_demasiadas_lineas() {
    var demasiados =
        IntStream.rangeClosed(0, Pedido.MAXIMO_LINEAS)
            .mapToObj(i -> new LineaPedido(UUID.randomUUID(), 1))
            .toList();

    assertThatThrownBy(() -> Pedido.colocar(idPedido, demasiados, "c", COLOCADO_EN))
        .isInstanceOf(PedidoInvalido.class)
        .hasMessageContaining("líneas");
  }

  @Test
  @DisplayName("REQ-19 · 002-AC-06 las cantidades están entre 1 y 1000")
  void debe_rechazar_el_pedido_cuando_una_cantidad_esta_fuera_de_rango() {
    for (var cantidad : new int[] {0, Pedido.CANTIDAD_MAXIMA + 1}) {
      var linea = List.of(new LineaPedido(UUID.randomUUID(), cantidad));

      assertThatThrownBy(() -> Pedido.colocar(idPedido, linea, "c", COLOCADO_EN))
          .isInstanceOf(PedidoInvalido.class)
          .hasMessageContaining("cantidad");
    }
  }

  @Test
  @DisplayName("REQ-19 · 002 RN-04 un producto aparece en una sola línea")
  void debe_rechazar_el_pedido_cuando_un_producto_se_repite() {
    var producto = UUID.randomUUID();
    var repetido = List.of(new LineaPedido(producto, 1), new LineaPedido(producto, 2));

    assertThatThrownBy(() -> Pedido.colocar(idPedido, repetido, "c", COLOCADO_EN))
        .isInstanceOf(LineaProductoDuplicada.class);
  }

  @Test
  @DisplayName("REQ-07 · 003 RN-05 una reserva confirma un pedido pendiente")
  void debe_confirmar_cuando_la_reserva_tiene_exito() {
    var pedido = colocado();

    pedido.confirmarReserva(LATER);

    assertThat(pedido.estado()).isEqualTo(EstadoPedido.CONFIRMED);
    assertThat(pedido.actualizadoEn()).isEqualTo(LATER);
    assertThat(pedido.extraerEventos()).containsExactly(new PedidoConfirmado(idPedido));
  }

  @Test
  @DisplayName("REQ-07 · 003 RN-05 una reserva rechazada rechaza con el motivo de inventario")
  void debe_rechazar_con_motivo_cuando_la_reserva_es_rechazada() {
    var pedido = colocado();

    pedido.rechazarReserva(MotivoRechazoPedido.INSUFFICIENT_STOCK, LATER);

    assertThat(pedido.estado()).isEqualTo(EstadoPedido.REJECTED);
    assertThat(pedido.motivoRechazo()).isEqualTo(MotivoRechazoPedido.INSUFFICIENT_STOCK);
    assertThat(pedido.extraerEventos())
        .containsExactly(new PedidoRechazado(idPedido, MotivoRechazoPedido.INSUFFICIENT_STOCK));
  }

  @Test
  @DisplayName("REQ-05 REQ-12 · 004 RN-02 cancelar libera el stock")
  void debe_cancelar_y_solicitar_liberacion_cuando_se_cancela() {
    var pedido = colocado();

    pedido.cancelar(LATER);

    assertThat(pedido.estado()).isEqualTo(EstadoPedido.CANCELLED);
    assertThat(pedido.extraerEventos())
        .containsExactly(
            new PedidoCancelado(idPedido),
            new LiberacionStockSolicitada(idPedido, CausaLiberacion.CANCELADO_POR_CLIENTE));
  }

  @Test
  @DisplayName("REQ-09 · 004-AC-08 cancelar un pedido cancelado no cambia nada")
  void debe_no_registrar_nada_cuando_se_cancela_dos_veces() {
    var pedido = colocado();
    pedido.cancelar(LATER);
    pedido.extraerEventos();

    pedido.cancelar(LATER.plusSeconds(1));

    assertThat(pedido.estado()).isEqualTo(EstadoPedido.CANCELLED);
    assertThat(pedido.actualizadoEn()).isEqualTo(LATER);
    assertThat(pedido.extraerEventos()).isEmpty();
  }

  @Test
  @DisplayName("REQ-27 · 004-AC-04 una reserva tardía sobre un pedido cancelado se compensa")
  void debe_mantener_cancelado_y_compensar_cuando_la_reserva_llega_tarde() {
    var pedido = colocado();
    pedido.cancelar(LATER);
    pedido.extraerEventos();

    pedido.confirmarReserva(LATER.plusSeconds(1));

    assertThat(pedido.estado()).isEqualTo(EstadoPedido.CANCELLED);
    assertThat(pedido.extraerEventos())
        .containsExactly(
            new LiberacionStockSolicitada(idPedido, CausaLiberacion.COMPENSACION_RESERVA_TARDIA));
  }

  @Test
  @DisplayName("REQ-27 · 003-AC-10 una saga estancada agota el tiempo y libera")
  void debe_rechazar_con_timeout_y_solicitar_liberacion_cuando_expira() {
    var pedido = colocado();

    pedido.expirar(LATER);

    assertThat(pedido.estado()).isEqualTo(EstadoPedido.REJECTED);
    assertThat(pedido.motivoRechazo()).isEqualTo(MotivoRechazoPedido.TIMEOUT);
    assertThat(pedido.extraerEventos())
        .containsExactly(
            new PedidoRechazado(idPedido, MotivoRechazoPedido.TIMEOUT),
            new LiberacionStockSolicitada(idPedido, CausaLiberacion.TIMEOUT_SAGA));
  }

  @Test
  @DisplayName("REQ-08 · un pedido restaurado conserva su estado y no registra eventos")
  void debe_restaurar_el_estado_sin_eventos() {
    var pedido =
        Pedido.restaurar(
            idPedido,
            EstadoPedido.REJECTED,
            MotivoRechazoPedido.UNKNOWN_PRODUCT,
            lineas,
            "corr-1",
            COLOCADO_EN,
            LATER,
            COLOCADO_EN);

    assertThat(pedido.estado()).isEqualTo(EstadoPedido.REJECTED);
    assertThat(pedido.motivoRechazo()).isEqualTo(MotivoRechazoPedido.UNKNOWN_PRODUCT);
    assertThat(pedido.lineas()).isEqualTo(lineas);
    assertThat(pedido.extraerEventos()).isEmpty();
  }

  private Pedido colocado() {
    var pedido = Pedido.colocar(idPedido, lineas, "corr-1", COLOCADO_EN);
    pedido.extraerEventos();
    return pedido;
  }
}
