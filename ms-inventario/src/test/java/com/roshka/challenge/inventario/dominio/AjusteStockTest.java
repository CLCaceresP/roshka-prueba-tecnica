package com.roshka.challenge.inventario.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AjusteStockTest {

  private static final UUID PRODUCTO = UUID.randomUUID();

  @Test
  @DisplayName("EXT-03 · 014-AC-05 lo disponible debe estar entre 0 y el máximo")
  void debe_rechazar_el_ajuste_cuando_lo_disponible_esta_fuera_de_rango() {
    assertThatThrownBy(() -> AjusteStock.de(PRODUCTO, -1, null))
        .isInstanceOf(ProductoInvalido.class)
        .hasMessageContaining("available")
        .hasMessageContaining("entre 0 y 10000000");
    assertThatThrownBy(() -> AjusteStock.de(PRODUCTO, 10_000_001, null))
        .isInstanceOf(ProductoInvalido.class)
        .hasMessageContaining("available");
    assertThat(AjusteStock.de(PRODUCTO, 0, null).disponible()).isZero();
    assertThat(AjusteStock.de(PRODUCTO, 10_000_000, null).disponible()).isEqualTo(10_000_000);
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-05 el motivo se normaliza y se rechaza si es muy largo")
  void debe_normalizar_el_motivo_y_rechazar_el_que_supera_el_limite() {
    var normalizado = AjusteStock.de(PRODUCTO, 5, "  Reposición   semanal ");
    var vacio = AjusteStock.de(PRODUCTO, 5, "   ");

    assertThat(normalizado.motivo()).isEqualTo("Reposición semanal");
    assertThat(vacio.motivo()).isNull();
    assertThatThrownBy(() -> AjusteStock.de(PRODUCTO, 5, "x".repeat(201)))
        .isInstanceOf(ProductoInvalido.class)
        .hasMessageContaining("reason")
        .hasMessageContaining("200");
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-05 el motivo no admite caracteres de control")
  void debe_rechazar_el_motivo_cuando_trae_caracteres_de_control() {
    assertThatThrownBy(() -> AjusteStock.de(PRODUCTO, 5, "mal\u0007motivo"))
        .isInstanceOf(ProductoInvalido.class)
        .hasMessageContaining("caracteres de control");
  }
}
