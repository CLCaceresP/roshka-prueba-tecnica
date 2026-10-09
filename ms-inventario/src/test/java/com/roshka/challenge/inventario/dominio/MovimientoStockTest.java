package com.roshka.challenge.inventario.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MovimientoStockTest {

  private static final Instant AHORA = Instant.parse("2026-10-10T12:00:00Z");
  private static final UUID ID = UUID.randomUUID();

  @Test
  @DisplayName("EXT-03 · 014-RN-05 si sube lo disponible el movimiento es ADD por la diferencia")
  void debe_ser_un_alta_por_la_diferencia_cuando_sube_lo_disponible() {
    var anterior = new Producto(ID, "p", 7, 5, 2);

    var movimiento =
        MovimientoStock.entre(anterior, anterior.conDisponible(12), "reposición", AHORA);

    assertThat(movimiento)
        .hasValueSatisfying(
            m -> {
              assertThat(m.tipo()).isEqualTo(TipoAjuste.ADD);
              assertThat(m.cantidad()).isEqualTo(7);
              assertThat(m.totalDespues()).isEqualTo(14);
              assertThat(m.disponibleDespues()).isEqualTo(12);
              assertThat(m.motivo()).isEqualTo("reposición");
              assertThat(m.idProducto()).isEqualTo(ID);
              assertThat(m.creadoEn()).isEqualTo(AHORA);
            });
  }

  @Test
  @DisplayName("EXT-03 · 014-RN-05 si baja lo disponible el movimiento es REMOVE por la diferencia")
  void debe_ser_una_baja_por_la_diferencia_cuando_baja_lo_disponible() {
    var anterior = new Producto(ID, "p", 10, 8, 2);

    var movimiento = MovimientoStock.entre(anterior, anterior.conDisponible(0), null, AHORA);

    assertThat(movimiento)
        .hasValueSatisfying(
            m -> {
              assertThat(m.tipo()).isEqualTo(TipoAjuste.REMOVE);
              assertThat(m.cantidad()).isEqualTo(8);
              assertThat(m.totalDespues()).isEqualTo(2);
            });
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-06 si no cambió no hay movimiento")
  void debe_ser_vacio_cuando_lo_disponible_no_cambia() {
    var anterior = new Producto(ID, "p", 7, 5, 2);

    assertThat(MovimientoStock.entre(anterior, anterior.conDisponible(5), null, AHORA)).isEmpty();
  }
}
