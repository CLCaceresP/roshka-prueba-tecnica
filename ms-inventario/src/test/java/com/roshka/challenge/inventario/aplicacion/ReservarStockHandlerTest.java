package com.roshka.challenge.inventario.aplicacion;

import static com.roshka.challenge.inventario.aplicacion.Comandos.linea;
import static com.roshka.challenge.inventario.aplicacion.Comandos.reservar;
import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.contrato.MotivoRechazoStock;
import com.roshka.challenge.contrato.StockRechazado;
import com.roshka.challenge.contrato.StockReservado;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ResultadoManejo;
import com.roshka.challenge.inventario.dominio.EstadoReserva;
import com.roshka.challenge.inventario.dominio.Producto;
import com.roshka.challenge.inventario.dominio.Reserva;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservarStockHandlerTest {

  private final InventarioEnMemoria inventario = new InventarioEnMemoria();
  private final Clock reloj = Clock.fixed(Instant.parse("2026-10-09T12:00:05Z"), ZoneOffset.UTC);
  private final ReservarStockHandler manejador =
      new ReservarStockHandler(inventario, inventario, inventario, inventario, inventario, reloj);

  private final UUID idPedido = UUID.randomUUID();
  private final UUID teclado = productoCon(5);
  private final UUID raton = productoCon(2);

  @Test
  @DisplayName("REQ-11 · 001-AC-04 reserva todas las líneas y confirma con correlación")
  void debe_reservar_todas_las_lineas_y_confirmar_cuando_el_stock_es_suficiente() {
    var comando = reservar(idPedido, linea(teclado, 3), linea(raton, 2));

    assertThat(manejador.manejar(comando)).isEqualTo(ResultadoManejo.APLICADO);

    assertThat(inventario.stock(teclado).disponible()).isEqualTo(2);
    assertThat(inventario.stock(teclado).reservado()).isEqualTo(3);
    assertThat(inventario.stock(raton).disponible()).isZero();
    assertThat(inventario.reservas.get(idPedido).estado()).isEqualTo(EstadoReserva.RESERVED);
    assertThat(inventario.outbox)
        .singleElement()
        .satisfies(
            publicado -> {
              assertThat(publicado.topico()).isEqualTo(Topicos.INVENTARIO_EVENTOS);
              var respuesta = publicado.sobre();
              assertThat(respuesta.tipo()).isEqualTo(TiposMensaje.STOCK_RESERVADO);
              assertThat(respuesta.idAgregado()).isEqualTo(idPedido);
              assertThat(respuesta.idCausa()).isEqualTo(comando.idMensaje());
              assertThat(respuesta.idCorrelacion()).isEqualTo(comando.idCorrelacion());
              assertThat(respuesta.ocurridoEn()).isEqualTo(reloj.instant());
              assertThat(respuesta.carga()).isInstanceOf(StockReservado.class);
            });
  }

  @Test
  @DisplayName("REQ-11 REQ-13 · 001-AC-05 el stock insuficiente rechaza el pedido completo")
  void debe_rechazar_sin_tocar_el_stock_cuando_alguna_linea_es_insuficiente() {
    manejador.manejar(reservar(idPedido, linea(teclado, 3), linea(raton, 3)));

    assertThat(inventario.stock(teclado).disponible()).isEqualTo(5);
    assertThat(inventario.stock(raton).disponible()).isEqualTo(2);
    assertThat(inventario.reservas.get(idPedido).estado()).isEqualTo(EstadoReserva.REJECTED);
    assertThat(inventario.outbox.getFirst().sobre().carga())
        .isEqualTo(
            new StockRechazado(idPedido, MotivoRechazoStock.STOCK_INSUFICIENTE, List.of(raton)));
  }

  @Test
  @DisplayName("REQ-11 · 001-AC-06 un producto desconocido rechaza el pedido completo")
  void debe_rechazar_como_producto_desconocido_cuando_un_producto_no_existe() {
    var fantasma = UUID.randomUUID();

    manejador.manejar(reservar(idPedido, linea(teclado, 1), linea(fantasma, 1)));

    assertThat(inventario.stock(teclado).disponible()).isEqualTo(5);
    assertThat(inventario.outbox.getFirst().sobre().carga())
        .isEqualTo(
            new StockRechazado(
                idPedido, MotivoRechazoStock.PRODUCTO_DESCONOCIDO, List.of(fantasma)));
  }

  @Test
  @DisplayName("REQ-14 REQ-26 · 003-AC-04 el mismo idMensaje tiene efecto una sola vez")
  void debe_ignorar_la_entrega_cuando_el_mensaje_ya_fue_procesado() {
    var comando = reservar(idPedido, linea(teclado, 3));

    manejador.manejar(comando);
    var segundo = manejador.manejar(comando);
    var tercero = manejador.manejar(comando);

    assertThat(List.of(segundo, tercero)).containsOnly(ResultadoManejo.DUPLICADO);
    assertThat(inventario.stock(teclado).reservado()).isEqualTo(3);
    assertThat(inventario.outbox).hasSize(1);
  }

  @Test
  @DisplayName("REQ-15 · 004 tabla: un ReservarStock nuevo para un pedido RESERVED solo reconfirma")
  void debe_confirmar_de_nuevo_sin_reservar_dos_veces_cuando_el_pedido_ya_esta_reservado() {
    manejador.manejar(reservar(idPedido, linea(teclado, 3)));

    manejador.manejar(reservar(idPedido, linea(teclado, 3)));

    assertThat(inventario.stock(teclado).reservado()).isEqualTo(3);
    assertThat(inventario.outbox)
        .extracting(publicado -> publicado.sobre().tipo())
        .containsExactly(TiposMensaje.STOCK_RESERVADO, TiposMensaje.STOCK_RESERVADO);
  }

  @Test
  @DisplayName("REQ-27 · 004-AC-05 un tombstone rechaza la reserva sin tocar el stock")
  void debe_rechazar_como_liberado_cuando_el_pedido_fue_liberado_antes_de_reservar() {
    inventario.insertar(Reserva.tombstone(idPedido));

    manejador.manejar(reservar(idPedido, linea(teclado, 3)));

    assertThat(inventario.stock(teclado).disponible()).isEqualTo(5);
    assertThat(inventario.outbox.getFirst().sobre().carga())
        .isEqualTo(new StockRechazado(idPedido, MotivoRechazoStock.RESERVA_LIBERADA, List.of()));
  }

  private UUID productoCon(int cantidad) {
    var producto = Producto.registrar(UUID.randomUUID(), "product-" + UUID.randomUUID(), cantidad);
    inventario.insertar(producto);
    return producto.id();
  }
}
