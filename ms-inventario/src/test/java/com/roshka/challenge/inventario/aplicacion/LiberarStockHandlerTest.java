package com.roshka.challenge.inventario.aplicacion;

import static com.roshka.challenge.inventario.aplicacion.Comandos.liberar;
import static com.roshka.challenge.inventario.aplicacion.Comandos.linea;
import static com.roshka.challenge.inventario.aplicacion.Comandos.reservar;
import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.contrato.ResultadoLiberacion;
import com.roshka.challenge.contrato.StockLiberado;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ResultadoManejo;
import com.roshka.challenge.inventario.dominio.EstadoReserva;
import com.roshka.challenge.inventario.dominio.Producto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LiberarStockHandlerTest {

  private final InventarioEnMemoria inventario = new InventarioEnMemoria();
  private final Clock reloj = Clock.fixed(Instant.parse("2026-10-09T12:00:05Z"), ZoneOffset.UTC);
  private final ReservarStockHandler manejadorReserva =
      new ReservarStockHandler(inventario, inventario, inventario, inventario, inventario, reloj);
  private final LiberarStockHandler manejador =
      new LiberarStockHandler(inventario, inventario, inventario, inventario, reloj);

  private final UUID idPedido = UUID.randomUUID();
  private final UUID teclado = productoCon(5);

  @Test
  @DisplayName("REQ-12 · 001-AC-08 liberar una reserva devuelve exactamente lo reservado")
  void debe_devolver_el_stock_reservado_cuando_la_reserva_esta_reservada() {
    manejadorReserva.manejar(reservar(idPedido, linea(teclado, 3)));
    var comando = liberar(idPedido);

    assertThat(manejador.manejar(comando)).isEqualTo(ResultadoManejo.APLICADO);

    assertThat(inventario.stock(teclado).disponible()).isEqualTo(5);
    assertThat(inventario.stock(teclado).reservado()).isZero();
    assertThat(inventario.reservas.get(idPedido).estado()).isEqualTo(EstadoReserva.RELEASED);
    var respuesta = inventario.outbox.getLast();
    assertThat(respuesta.topico()).isEqualTo(Topicos.INVENTARIO_EVENTOS);
    assertThat(respuesta.sobre().idCausa()).isEqualTo(comando.idMensaje());
    assertThat(respuesta.sobre().carga())
        .isEqualTo(new StockLiberado(idPedido, ResultadoLiberacion.DEVUELTO));
  }

  @Test
  @DisplayName("REQ-27 · 004-AC-05 liberar antes de reservar registra un tombstone")
  void debe_registrar_tombstone_sin_tocar_el_stock_cuando_no_habia_nada_reservado() {
    manejador.manejar(liberar(idPedido));

    assertThat(inventario.stock(teclado).disponible()).isEqualTo(5);
    assertThat(inventario.reservas.get(idPedido).estado()).isEqualTo(EstadoReserva.RELEASED);
    assertThat(inventario.reservas.get(idPedido).lineas()).isEmpty();
    assertThat(inventario.outbox.getLast().sobre().carga())
        .isEqualTo(new StockLiberado(idPedido, ResultadoLiberacion.TOMBSTONE_REGISTRADO));
  }

  @Test
  @DisplayName("REQ-12 REQ-15 · 004-AC-11 un segundo LiberarStock no vuelve a devolver stock")
  void debe_devolver_el_stock_una_vez_cuando_llegan_dos_liberaciones_distintas() {
    manejadorReserva.manejar(reservar(idPedido, linea(teclado, 3)));

    manejador.manejar(liberar(idPedido));
    manejador.manejar(liberar(idPedido));

    assertThat(inventario.stock(teclado).disponible()).isEqualTo(5);
    assertThat(inventario.lotesLiberados).hasSize(1);
    assertThat(inventario.outbox.getLast().sobre().carga())
        .isEqualTo(new StockLiberado(idPedido, ResultadoLiberacion.NADA_QUE_LIBERAR));
  }

  @Test
  @DisplayName(
      "REQ-14 REQ-26 · 003-AC-04 el mismo LiberarStock entregado dos veces tiene efecto una vez")
  void debe_ignorar_la_entrega_cuando_la_liberacion_ya_fue_procesada() {
    manejadorReserva.manejar(reservar(idPedido, linea(teclado, 3)));
    var comando = liberar(idPedido);
    manejador.manejar(comando);
    var respuestasAntes = inventario.outbox.size();

    assertThat(manejador.manejar(comando)).isEqualTo(ResultadoManejo.DUPLICADO);
    assertThat(inventario.outbox).hasSize(respuestasAntes);
    assertThat(inventario.lotesLiberados).hasSize(1);
  }

  private UUID productoCon(int cantidad) {
    var producto = Producto.registrar(UUID.randomUUID(), "product-" + UUID.randomUUID(), cantidad);
    inventario.insertar(producto);
    return producto.id();
  }
}
