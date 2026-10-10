package com.roshka.challenge.pedidos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.LineaStock;
import com.roshka.challenge.contrato.MotivoLiberacion;
import com.roshka.challenge.contrato.ReservarStock;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.pedidos.aplicacion.saga.SagaReserva;
import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.MotivoRechazoPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import com.roshka.challenge.pedidos.dominio.PedidoNoCancelable;
import com.roshka.challenge.pedidos.dominio.PedidoNoEncontrado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PedidoCasosUsoTest {

  private final Clock reloj = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);
  private final PedidosEnMemoria almacen = new PedidosEnMemoria();
  private final SagaReserva saga = new SagaReserva(almacen, almacen, reloj);
  private final ColocacionPedido colocacion = new ColocacionPedido(almacen, saga, reloj);
  private final CancelacionPedido cancelacion = new CancelacionPedido(almacen, saga, reloj);
  private final ConsultaPedido consulta = new ConsultaPedido(almacen);

  private final UUID producto = UUID.randomUUID();

  @Test
  @DisplayName("REQ-03 REQ-06 · 002-AC-01/02 colocar guarda PENDING y registra ReservarStock")
  void debe_guardar_pedido_pendiente_y_registrar_reservar_stock_cuando_se_coloca() {
    var pedido = colocacion.colocar(List.of(new LineaPedido(producto, 2)), "corr-1");

    assertThat(almacen.pedidos.get(pedido.id()).estado()).isEqualTo(EstadoPedido.PENDING);
    assertThat(almacen.outbox)
        .singleElement()
        .satisfies(
            publicado -> {
              assertThat(publicado.topico()).isEqualTo(Topicos.INVENTARIO_COMANDOS);
              var comando = publicado.sobre();
              assertThat(comando.tipo()).isEqualTo(TiposMensaje.RESERVAR_STOCK);
              assertThat(comando.idAgregado()).isEqualTo(pedido.id());
              assertThat(comando.idCorrelacion()).isEqualTo("corr-1");
              assertThat(comando.idCausa()).isNull();
              assertThat(comando.ocurridoEn()).isEqualTo(reloj.instant());
              assertThat(comando.carga())
                  .isEqualTo(new ReservarStock(pedido.id(), List.of(new LineaStock(producto, 2))));
            });
  }

  @Test
  @DisplayName("REQ-04 · 002-AC-04/05 consultar devuelve el pedido o informa que falta")
  void debe_devolver_el_pedido_cuando_existe_y_fallar_en_otro_caso() {
    var pedido = colocacion.colocar(List.of(new LineaPedido(producto, 2)), "corr-1");

    assertThat(consulta.get(pedido.id())).isSameAs(almacen.pedidos.get(pedido.id()));
    assertThatThrownBy(() -> consulta.get(UUID.randomUUID()))
        .isInstanceOf(PedidoNoEncontrado.class);
  }

  @Test
  @DisplayName("REQ-05 REQ-12 · 004 RN-02 cancelar un pedido pendiente libera su stock")
  void debe_cancelar_y_registrar_liberar_stock_cuando_el_pedido_esta_pendiente() {
    var pedido = colocacion.colocar(List.of(new LineaPedido(producto, 2)), "corr-1");

    var cancelado = cancelacion.cancelar(pedido.id(), "corr-cancel");

    assertThat(cancelado.estado()).isEqualTo(EstadoPedido.CANCELLED);
    assertThat(almacen.versiones.get(pedido.id())).isEqualTo(1);
    var liberar = almacen.outbox.getLast().sobre();
    assertThat(liberar.tipo()).isEqualTo(TiposMensaje.LIBERAR_STOCK);
    assertThat(liberar.idCorrelacion()).isEqualTo("corr-cancel");
    assertThat(liberar.carga())
        .isEqualTo(new LiberarStock(pedido.id(), MotivoLiberacion.CANCELADO_POR_CLIENTE));
  }

  @Test
  @DisplayName("REQ-09 · 004-AC-08 cancelar un pedido cancelado no emite nada")
  void debe_no_cambiar_nada_cuando_el_pedido_ya_esta_cancelado() {
    var pedido = colocacion.colocar(List.of(new LineaPedido(producto, 2)), "corr-1");
    cancelacion.cancelar(pedido.id(), "corr-cancel");
    var mensajes = almacen.outbox.size();

    var otraVez = cancelacion.cancelar(pedido.id(), "corr-cancel-2");

    assertThat(otraVez.estado()).isEqualTo(EstadoPedido.CANCELLED);
    assertThat(almacen.outbox).hasSize(mensajes);
    assertThat(almacen.versiones.get(pedido.id())).isEqualTo(1);
  }

  @Test
  @DisplayName("REQ-21 · 004-AC-09 un pedido rechazado no se puede cancelar")
  void debe_negar_la_cancelacion_cuando_el_pedido_fue_rechazado() {
    var pedido =
        Pedido.restaurar(
            UUID.randomUUID(),
            EstadoPedido.REJECTED,
            MotivoRechazoPedido.INSUFFICIENT_STOCK,
            List.of(new LineaPedido(producto, 2)),
            "corr-1",
            reloj.instant(),
            reloj.instant(),
            reloj.instant());
    almacen.insertar(pedido);

    assertThatThrownBy(() -> cancelacion.cancelar(pedido.id(), "corr-cancel"))
        .isInstanceOf(PedidoNoCancelable.class);
    assertThat(almacen.outbox).isEmpty();
  }

  @Test
  @DisplayName("REQ-21 · cancelar un pedido desconocido informa que falta")
  void debe_fallar_con_no_encontrado_cuando_se_cancela_un_pedido_desconocido() {
    assertThatThrownBy(() -> cancelacion.cancelar(UUID.randomUUID(), "corr"))
        .isInstanceOf(PedidoNoEncontrado.class);
  }
}
