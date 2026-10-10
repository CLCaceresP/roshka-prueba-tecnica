package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.pedidos.dominio.ParametroInvalido;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FechaHttpTest {

  @Test
  @DisplayName("EXT-02 · 013-AC-01 una fecha MM/dd/yyyy se interpreta como tal")
  void debe_interpretar_la_fecha_cuando_tiene_el_formato_mes_dia_anio() {
    assertThat(FechaHttp.parsear("from", "10/01/2026")).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(FechaHttp.parsear("from", " 02/28/2026 ")).isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-02 un parámetro ausente o en blanco es null")
  void debe_devolver_null_cuando_el_parametro_no_viene() {
    assertThat(FechaHttp.parsear("from", null)).isNull();
    assertThat(FechaHttp.parsear("from", "  ")).isNull();
  }

  @Test
  @DisplayName(
      "REQ-19 · 013-AC-05 otro formato o una fecha inexistente se rechaza y dice el formato")
  void debe_rechazar_la_fecha_cuando_no_tiene_el_formato_o_no_existe() {
    for (var invalida : new String[] {"2026-10-01", "13/45/2026", "02/30/2026", "10/1/26"}) {
      assertThatThrownBy(() -> FechaHttp.parsear("to", invalida))
          .isInstanceOf(ParametroInvalido.class)
          .hasMessageContaining("to")
          .hasMessageContaining("MM/dd/yyyy");
    }
  }
}
