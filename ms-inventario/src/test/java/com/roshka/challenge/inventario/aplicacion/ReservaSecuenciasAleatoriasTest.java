package com.roshka.challenge.inventario.aplicacion;

import static com.roshka.challenge.inventario.aplicacion.Comandos.liberar;
import static com.roshka.challenge.inventario.aplicacion.Comandos.linea;
import static com.roshka.challenge.inventario.aplicacion.Comandos.reservar;
import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.ReservarStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.inventario.dominio.Producto;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propiedad de la spec 004 (AC-13), lado inventario: sea cual sea el orden de llegada de
 * ReservarStock y LiberarStock de un pedido, y las veces que se entregue cada uno, el stock se
 * conserva y solo queda retenido cuando no llegó ninguna liberación. Las semillas son fijas para
 * que todo fallo sea reproducible.
 */
class ReservaSecuenciasAleatoriasTest {

  private static final int SECUENCIAS = 500;
  private static final int TOTAL = 5;

  @Test
  @DisplayName(
      "REQ-12 REQ-13 REQ-27 · 004-AC-13 any arrival order of reserve/release, any duplicate")
  void debe_mantener_el_stock_consistente_para_cada_orden_de_llegada_y_duplicado() {
    for (long semilla = 1; semilla <= SECUENCIAS; semilla++) {
      var aleatorio = new Random(semilla);
      var inventario = new InventarioEnMemoria();
      var reloj = Clock.systemUTC();
      var manejadorReserva =
          new ReservarStockHandler(
              inventario, inventario, inventario, inventario, inventario, reloj);
      var manejadorLiberacion =
          new LiberarStockHandler(inventario, inventario, inventario, inventario, reloj);
      var producto = Producto.registrar(UUID.randomUUID(), "p-" + UUID.randomUUID(), TOTAL);
      inventario.insertar(producto);
      var idPedido = UUID.randomUUID();
      var cantidad = 1 + aleatorio.nextInt(TOTAL + 1);

      var mensajes = new ArrayList<SobreMensaje<?>>();
      var reservar = reservar(idPedido, linea(producto.id(), cantidad));
      mensajes.add(reservar);
      var liberaciones = aleatorio.nextInt(3);
      for (int i = 0; i < liberaciones; i++) {
        mensajes.add(liberar(idPedido));
      }
      var entregas = new ArrayList<SobreMensaje<?>>(mensajes);
      for (var mensaje : mensajes) {
        for (int copia = aleatorio.nextInt(3); copia > 0; copia--) {
          entregas.add(mensaje);
        }
      }
      Collections.shuffle(entregas, aleatorio);

      for (var entrega : entregas) {
        if (entrega.carga() instanceof ReservarStock) {
          manejadorReserva.manejar(convertir(entrega));
        } else {
          manejadorLiberacion.manejar(convertirLiberacion(entrega));
        }
      }

      var stock = inventario.stock(producto.id());
      var descrito = "seed " + semilla + ", quantity " + cantidad + ", releases " + liberaciones;
      assertThat(stock.disponible() + stock.reservado()).as(descrito).isEqualTo(TOTAL);
      assertThat(stock.disponible()).as(descrito).isGreaterThanOrEqualTo(0);
      var reservadoEsperado = liberaciones == 0 && cantidad <= TOTAL ? cantidad : 0;
      assertThat(stock.reservado()).as(descrito).isEqualTo(reservadoEsperado);
      assertThat(inventario.outbox)
          .as(descrito + ": one reply per distinct message")
          .hasSize(new HashSet<>(mensajes).size());
      assertThat(inventario.lotesLiberados).as(descrito).hasSizeLessThanOrEqualTo(1);
    }
  }

  @SuppressWarnings("unchecked")
  private static SobreMensaje<ReservarStock> convertir(SobreMensaje<?> sobre) {
    return (SobreMensaje<ReservarStock>) sobre;
  }

  @SuppressWarnings("unchecked")
  private static SobreMensaje<LiberarStock> convertirLiberacion(SobreMensaje<?> sobre) {
    return (SobreMensaje<LiberarStock>) sobre;
  }
}
