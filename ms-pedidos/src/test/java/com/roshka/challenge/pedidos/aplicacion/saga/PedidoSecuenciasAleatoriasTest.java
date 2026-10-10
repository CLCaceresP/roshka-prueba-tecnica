package com.roshka.challenge.pedidos.aplicacion.saga;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.MotivoRechazoStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.pedidos.aplicacion.BloqueoRegistrador;
import com.roshka.challenge.pedidos.aplicacion.CancelacionPedido;
import com.roshka.challenge.pedidos.aplicacion.ColocacionPedido;
import com.roshka.challenge.pedidos.aplicacion.PedidosEnMemoria;
import com.roshka.challenge.pedidos.aplicacion.Respuestas;
import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.MotivoRechazoPedido;
import com.roshka.challenge.pedidos.dominio.PedidoNoCancelable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propiedad de la spec 004 (AC-13), lado pedidos: sea cual sea el orden de llegada del desenlace de
 * inventario, una cancelación y un timeout de la saga, y las veces que se entregue la respuesta, el
 * pedido termina en un estado terminal y pide a inventario liberar exactamente cuando el stock
 * podría quedar retenido en vano. Las semillas son fijas para que todo fallo sea reproducible.
 */
class PedidoSecuenciasAleatoriasTest {

  private static final int SECUENCIAS = 500;

  private enum Entrada {
    DESENLACE,
    CANCELAR,
    TIMEOUT
  }

  @Test
  @DisplayName(
      "REQ-07 REQ-27 · 004-AC-13 cualquier orden de llegada del desenlace, la cancelación y el timeout")
  void debe_llegar_a_estado_terminal_y_liberar_correctamente_para_cada_orden_de_llegada() {
    for (long semilla = 1; semilla <= SECUENCIAS; semilla++) {
      var aleatorio = new Random(semilla);
      var reloj = new RelojPorPasos(Instant.parse("2026-10-09T12:00:00Z"));
      var almacen = new PedidosEnMemoria();
      var saga = new SagaReserva(almacen, almacen, reloj);
      var pasos = new PasosSaga(almacen, almacen, saga, reloj);
      var ajustes =
          new ConfiguracionSaga(Duration.ofSeconds(30), 100, Duration.ZERO, Duration.ofSeconds(10));
      var respuestas = new RespuestaInventarioHandler(new BloqueoRegistrador(), pasos, ajustes);
      var timeouts =
          new TimeoutSagaHandler(almacen, new BloqueoRegistrador(), pasos, ajustes, reloj);
      var cancelacion = new CancelacionPedido(almacen, saga, reloj);
      var idPedido =
          new ColocacionPedido(almacen, saga, reloj)
              .colocar(List.of(new LineaPedido(UUID.randomUUID(), 1)), "corr")
              .id();

      var reservado = aleatorio.nextBoolean();
      SobreMensaje<?> desenlace =
          reservado
              ? Respuestas.stockReservado(idPedido)
              : Respuestas.stockRechazado(idPedido, MotivoRechazoStock.STOCK_INSUFICIENTE);
      var entradas = new ArrayList<Entrada>();
      for (int copia = 1 + aleatorio.nextInt(3); copia > 0; copia--) {
        entradas.add(Entrada.DESENLACE);
      }
      if (aleatorio.nextBoolean()) {
        entradas.add(Entrada.CANCELAR);
      }
      if (aleatorio.nextBoolean()) {
        entradas.add(Entrada.TIMEOUT);
      }
      Collections.shuffle(entradas, aleatorio);

      for (var entrada : entradas) {
        switch (entrada) {
          case DESENLACE -> {
            if (reservado) {
              respuestas.alStockReservado(convertir(desenlace));
            } else {
              respuestas.alStockRechazado(convertir(desenlace));
            }
          }
          case CANCELAR -> {
            try {
              cancelacion.cancelar(idPedido, "corr-cancel");
            } catch (PedidoNoCancelable rechazadosYa) {
              // 409 para el cliente; no cambia nada.
            }
          }
          case TIMEOUT -> {
            reloj.avanzar(Duration.ofMinutes(1));
            timeouts.expirarEstancadas();
          }
        }
      }

      var pedido = almacen.pedidos.get(idPedido);
      var liberaciones =
          almacen.outbox.stream()
              .map(publicado -> publicado.sobre().carga())
              .filter(LiberarStock.class::isInstance)
              .map(carga -> ((LiberarStock) carga).motivo().name())
              .toList();
      var descrito = "seed " + semilla + ", reserved " + reservado + ", inputs " + entradas;

      var esperado = Esperado.despues(entradas, reservado);
      assertThat(pedido.estado()).as(descrito).isEqualTo(esperado.estado());
      assertThat(pedido.motivoRechazo()).as(descrito).isEqualTo(esperado.motivo());
      assertThat(liberaciones).as(descrito).isEqualTo(esperado.liberaciones());
    }
  }

  /**
   * Modelo de referencia de la tabla del Pedido de la spec 004. La respuesta con el desenlace es un
   * mismo mensaje entregado varias veces, así que solo cuenta su primera entrega; las demás son
   * duplicados.
   */
  private record Esperado(
      EstadoPedido estado, MotivoRechazoPedido motivo, List<String> liberaciones) {

    static Esperado despues(List<Entrada> entradas, boolean reservado) {
      var estado = EstadoPedido.PENDING;
      MotivoRechazoPedido motivo = null;
      var liberaciones = new ArrayList<String>();
      var desenlaceVisto = false;
      for (var entrada : entradas) {
        switch (entrada) {
          case DESENLACE -> {
            if (desenlaceVisto) {
              continue;
            }
            desenlaceVisto = true;
            if (estado == EstadoPedido.PENDING) {
              estado = reservado ? EstadoPedido.CONFIRMED : EstadoPedido.REJECTED;
              motivo = reservado ? null : MotivoRechazoPedido.INSUFFICIENT_STOCK;
            } else if (reservado) {
              liberaciones.add("COMPENSACION_RESERVA_TARDIA");
            }
          }
          case CANCELAR -> {
            if (estado == EstadoPedido.PENDING || estado == EstadoPedido.CONFIRMED) {
              estado = EstadoPedido.CANCELLED;
              liberaciones.add("CANCELADO_POR_CLIENTE");
            }
          }
          case TIMEOUT -> {
            if (estado == EstadoPedido.PENDING) {
              estado = EstadoPedido.REJECTED;
              motivo = MotivoRechazoPedido.TIMEOUT;
              liberaciones.add("TIMEOUT_SAGA");
            }
          }
        }
      }
      return new Esperado(estado, motivo, liberaciones);
    }
  }

  @SuppressWarnings("unchecked")
  private static <P> SobreMensaje<P> convertir(SobreMensaje<?> sobre) {
    return (SobreMensaje<P>) sobre;
  }

  private static final class RelojPorPasos extends Clock {

    private Instant ahora;

    RelojPorPasos(Instant ahora) {
      this.ahora = ahora;
    }

    void avanzar(Duration duracion) {
      ahora = ahora.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
      return this;
    }

    @Override
    public Instant instant() {
      return ahora;
    }
  }
}
