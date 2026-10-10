package com.roshka.challenge.pedidos.dominio;

import static com.roshka.challenge.pedidos.dominio.CausaLiberacion.CANCELADO_POR_CLIENTE;
import static com.roshka.challenge.pedidos.dominio.CausaLiberacion.COMPENSACION_RESERVA_TARDIA;
import static com.roshka.challenge.pedidos.dominio.CausaLiberacion.TIMEOUT_SAGA;
import static com.roshka.challenge.pedidos.dominio.EstadoPedido.CANCELLED;
import static com.roshka.challenge.pedidos.dominio.EstadoPedido.CONFIRMED;
import static com.roshka.challenge.pedidos.dominio.EstadoPedido.PENDING;
import static com.roshka.challenge.pedidos.dominio.EstadoPedido.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** La tabla del Pedido de la spec 004, una fila por celda (estado x entrada). */
class EstadoPedidoTest {

  private enum Entrada {
    CANCELAR(EstadoPedido::alCancelar),
    STOCK_RESERVADO(EstadoPedido::alStockReservado),
    STOCK_RECHAZADO(EstadoPedido::alStockRechazado),
    TIMEOUT(EstadoPedido::alTimeout);

    private final Function<EstadoPedido, Transicion> aplicar;

    Entrada(Function<EstadoPedido, Transicion> aplicar) {
      this.aplicar = aplicar;
    }
  }

  static Stream<Arguments> tabla() {
    return Stream.of(
        Arguments.of(PENDING, Entrada.CANCELAR, CANCELLED, CANCELADO_POR_CLIENTE),
        Arguments.of(PENDING, Entrada.STOCK_RESERVADO, CONFIRMED, null),
        Arguments.of(PENDING, Entrada.STOCK_RECHAZADO, REJECTED, null),
        Arguments.of(PENDING, Entrada.TIMEOUT, REJECTED, TIMEOUT_SAGA),
        Arguments.of(CONFIRMED, Entrada.CANCELAR, CANCELLED, CANCELADO_POR_CLIENTE),
        Arguments.of(CONFIRMED, Entrada.STOCK_RESERVADO, CONFIRMED, null),
        Arguments.of(CONFIRMED, Entrada.TIMEOUT, CONFIRMED, null),
        Arguments.of(CANCELLED, Entrada.CANCELAR, CANCELLED, null),
        Arguments.of(CANCELLED, Entrada.STOCK_RESERVADO, CANCELLED, COMPENSACION_RESERVA_TARDIA),
        Arguments.of(CANCELLED, Entrada.STOCK_RECHAZADO, CANCELLED, null),
        Arguments.of(CANCELLED, Entrada.TIMEOUT, CANCELLED, null),
        Arguments.of(REJECTED, Entrada.STOCK_RESERVADO, REJECTED, COMPENSACION_RESERVA_TARDIA),
        Arguments.of(REJECTED, Entrada.STOCK_RECHAZADO, REJECTED, null),
        Arguments.of(REJECTED, Entrada.TIMEOUT, REJECTED, null));
  }

  @ParameterizedTest(name = "{0} + {1} -> {2}, release {3}")
  @MethodSource("tabla")
  @DisplayName("REQ-08 REQ-27 · 004 tabla del Pedido: todas las celdas permitidas")
  void debe_seguir_la_tabla_de_la_spec_004_cuando_se_aplica_la_entrada(
      EstadoPedido desde, Entrada entrada, EstadoPedido objetivo, CausaLiberacion liberar) {
    assertThat(entrada.aplicar.apply(desde)).isEqualTo(new Transicion(objetivo, liberar));
  }

  @Test
  @DisplayName("REQ-21 · 004-AC-09 un pedido REJECTED no se puede cancelar")
  void debe_rechazar_la_cancelacion_cuando_el_pedido_esta_rechazado() {
    assertThatThrownBy(REJECTED::alCancelar).isInstanceOf(PedidoNoCancelable.class);
  }

  @Test
  @DisplayName("REQ-26 · 004-AC-12 StockRechazado sobre CONFIRMED es imposible")
  void debe_declarar_transicion_imposible_cuando_un_pedido_confirmado_recibe_stock_rechazado() {
    assertThatThrownBy(CONFIRMED::alStockRechazado).isInstanceOf(TransicionPedidoInvalida.class);
  }
}
