package com.roshka.challenge.pedidos.aplicacion.saga;

import static com.roshka.challenge.pedidos.aplicacion.Respuestas.stockLiberado;
import static com.roshka.challenge.pedidos.aplicacion.Respuestas.stockRechazado;
import static com.roshka.challenge.pedidos.aplicacion.Respuestas.stockReservado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.MotivoLiberacion;
import com.roshka.challenge.contrato.MotivoRechazoStock;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.pedidos.aplicacion.BloqueoRegistrador;
import com.roshka.challenge.pedidos.aplicacion.BloqueosPedido;
import com.roshka.challenge.pedidos.aplicacion.CancelacionPedido;
import com.roshka.challenge.pedidos.aplicacion.ColocacionPedido;
import com.roshka.challenge.pedidos.aplicacion.PedidosEnMemoria;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ResultadoManejo;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.BloqueoNoAdquirido;
import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.MotivoRechazoPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import com.roshka.challenge.pedidos.dominio.PedidoNoEncontrado;
import com.roshka.challenge.pedidos.dominio.TransicionPedidoInvalida;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SagaReservaTest {

  private static final Duration UMBRAL = Duration.ofSeconds(30);

  private final Instant colocadoEn = Instant.parse("2026-10-09T12:00:00Z");
  private final RelojMutable reloj = new RelojMutable(colocadoEn);
  private final PedidosEnMemoria almacen = new PedidosEnMemoria();
  private final BloqueoRegistrador bloqueo = new BloqueoRegistrador();
  private final SagaReserva saga = new SagaReserva(almacen, almacen, reloj);
  private final PasosSaga pasos = new PasosSaga(almacen, almacen, saga, reloj);
  private final ConfiguracionSaga ajustes =
      new ConfiguracionSaga(UMBRAL, 100, Duration.ofMillis(500), Duration.ofSeconds(10));
  private final RespuestaInventarioHandler respuestas =
      new RespuestaInventarioHandler(bloqueo, pasos, ajustes);
  private final TimeoutSagaHandler timeouts =
      new TimeoutSagaHandler(almacen, bloqueo, pasos, ajustes, reloj);
  private final Pedido pedido =
      new ColocacionPedido(almacen, saga, reloj)
          .colocar(List.of(new LineaPedido(UUID.randomUUID(), 1)), "corr-order");

  @Test
  @DisplayName("REQ-07 · 003 RN-05 StockReservado confirma bajo el bloqueo del pedido")
  void debe_confirmar_el_pedido_bajo_su_bloqueo_cuando_el_stock_esta_reservado() {
    var resultado = respuestas.alStockReservado(stockReservado(pedido.id()));

    assertThat(resultado).isEqualTo(ResultadoManejo.APLICADO);
    assertThat(almacen.pedidos.get(pedido.id()).estado()).isEqualTo(EstadoPedido.CONFIRMED);
    assertThat(bloqueo.adquirido).containsExactly(BloqueosPedido.nombrePara(pedido.id()));
    assertThat(almacen.outbox).hasSize(1);
  }

  @Test
  @DisplayName("REQ-07 · 003-AC-02/03 StockRechazado rechaza con el motivo de inventario")
  void debe_rechazar_el_pedido_con_motivo_cuando_el_stock_es_rechazado() {
    respuestas.alStockRechazado(
        stockRechazado(pedido.id(), MotivoRechazoStock.PRODUCTO_DESCONOCIDO));

    var almacenado = almacen.pedidos.get(pedido.id());
    assertThat(almacenado.estado()).isEqualTo(EstadoPedido.REJECTED);
    assertThat(almacenado.motivoRechazo()).isEqualTo(MotivoRechazoPedido.UNKNOWN_PRODUCT);
  }

  @Test
  @DisplayName("REQ-14 REQ-26 · 003-AC-05 una respuesta duplicada no cambia nada")
  void debe_ignorar_la_respuesta_cuando_el_mensaje_ya_fue_procesado() {
    var respuesta = stockReservado(pedido.id());
    respuestas.alStockReservado(respuesta);
    var version = almacen.versiones.get(pedido.id());

    assertThat(respuestas.alStockReservado(respuesta)).isEqualTo(ResultadoManejo.DUPLICADO);
    assertThat(almacen.versiones.get(pedido.id())).isEqualTo(version);
  }

  @Test
  @DisplayName("REQ-27 · 004-AC-04 una reserva que llega después de la cancelación se compensa")
  void debe_compensar_cuando_la_reserva_llega_despues_de_la_cancelacion() {
    new CancelacionPedido(almacen, saga, reloj).cancelar(pedido.id(), "corr-cancel");
    var tardio = stockReservado(pedido.id());

    respuestas.alStockReservado(tardio);

    assertThat(almacen.pedidos.get(pedido.id()).estado()).isEqualTo(EstadoPedido.CANCELLED);
    var compensacion = almacen.outbox.getLast().sobre();
    assertThat(compensacion.tipo()).isEqualTo(TiposMensaje.LIBERAR_STOCK);
    assertThat(compensacion.idCausa()).isEqualTo(tardio.idMensaje());
    assertThat(compensacion.idCorrelacion()).isEqualTo(tardio.idCorrelacion());
    assertThat(compensacion.carga())
        .isEqualTo(new LiberarStock(pedido.id(), MotivoLiberacion.COMPENSACION_RESERVA_TARDIA));
  }

  @Test
  @DisplayName("REQ-26 · 004-AC-12 StockRechazado sobre un pedido confirmado es imposible")
  void debe_fallar_sin_cambios_cuando_un_pedido_confirmado_recibe_stock_rechazado() {
    respuestas.alStockReservado(stockReservado(pedido.id()));

    assertThatThrownBy(
            () ->
                respuestas.alStockRechazado(
                    stockRechazado(pedido.id(), MotivoRechazoStock.STOCK_INSUFICIENTE)))
        .isInstanceOf(TransicionPedidoInvalida.class);
    assertThat(almacen.pedidos.get(pedido.id()).estado()).isEqualTo(EstadoPedido.CONFIRMED);
  }

  @Test
  @DisplayName("REQ-12 · StockLiberado se confirma sin cambiar el pedido")
  void debe_confirmar_sin_cambios_cuando_el_stock_ya_fue_liberado() {
    new CancelacionPedido(almacen, saga, reloj).cancelar(pedido.id(), "corr-cancel");
    var version = almacen.versiones.get(pedido.id());

    assertThat(respuestas.alStockLiberado(stockLiberado(pedido.id())))
        .isEqualTo(ResultadoManejo.APLICADO);
    assertThat(almacen.versiones.get(pedido.id())).isEqualTo(version);
  }

  @Test
  @DisplayName(
      "REQ-25 · una respuesta para un pedido bloqueado en otro lado se reintenta más tarde")
  void debe_no_aplicar_la_respuesta_cuando_el_bloqueo_del_pedido_lo_retiene_otro() {
    bloqueo.retenidoPorOtro.add(BloqueosPedido.nombrePara(pedido.id()));

    assertThatThrownBy(() -> respuestas.alStockReservado(stockReservado(pedido.id())))
        .isInstanceOf(BloqueoNoAdquirido.class);
    assertThat(almacen.pedidos.get(pedido.id()).estado()).isEqualTo(EstadoPedido.PENDING);
    assertThat(almacen.procesado).isEmpty();
  }

  @Test
  @DisplayName("REQ-26 · una respuesta para un pedido desconocido es una violación del contrato")
  void debe_fallar_cuando_la_respuesta_se_refiere_a_un_pedido_desconocido() {
    assertThatThrownBy(() -> respuestas.alStockReservado(stockReservado(UUID.randomUUID())))
        .isInstanceOf(PedidoNoEncontrado.class);
  }

  @Test
  @DisplayName("REQ-07 REQ-27 · 003-AC-10 un pedido estancado agota el tiempo y libera su stock")
  void debe_expirar_pedido_estancado_y_registrar_liberacion_cuando_se_pasa_el_umbral() {
    reloj.avanzar(UMBRAL.plusSeconds(1));

    assertThat(timeouts.expirarEstancadas()).isEqualTo(1);

    var almacenado = almacen.pedidos.get(pedido.id());
    assertThat(almacenado.estado()).isEqualTo(EstadoPedido.REJECTED);
    assertThat(almacenado.motivoRechazo()).isEqualTo(MotivoRechazoPedido.TIMEOUT);
    var liberar = almacen.outbox.getLast().sobre();
    assertThat(liberar.idCorrelacion()).isEqualTo("corr-order");
    assertThat(liberar.carga())
        .isEqualTo(new LiberarStock(pedido.id(), MotivoLiberacion.TIMEOUT_SAGA));
  }

  @Test
  @DisplayName("REQ-07 · 0013 los pedidos recientes o ya decididos no se expiran")
  void debe_dejar_los_pedidos_cuando_son_recientes_o_ya_estan_decididos() {
    reloj.avanzar(UMBRAL.minusSeconds(1));
    assertThat(timeouts.expirarEstancadas()).isZero();

    respuestas.alStockReservado(stockReservado(pedido.id()));
    reloj.avanzar(UMBRAL);
    assertThat(timeouts.expirarEstancadas()).isZero();
    assertThat(almacen.pedidos.get(pedido.id()).estado()).isEqualTo(EstadoPedido.CONFIRMED);
  }

  private static final class RelojMutable extends Clock {

    private Instant ahora;

    RelojMutable(Instant ahora) {
      this.ahora = ahora;
    }

    void avanzar(Duration duracion) {
      ahora = ahora.plus(duracion);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zona) {
      return this;
    }

    @Override
    public Instant instant() {
      return ahora;
    }
  }
}
