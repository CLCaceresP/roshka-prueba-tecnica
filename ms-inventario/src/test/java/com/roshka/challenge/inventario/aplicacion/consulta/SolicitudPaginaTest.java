package com.roshka.challenge.inventario.aplicacion.consulta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.inventario.dominio.ParametroInvalido;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SolicitudPaginaTest {

  private static final ConfiguracionPaginacion LIMITES = new ConfiguracionPaginacion(20, 100);

  @Test
  @DisplayName(
      "EXT-01 · 012-AC-02 sin parámetros se usa la primera página con el tamaño por defecto")
  void debe_usar_los_valores_por_defecto_cuando_no_se_indican_parametros() {
    var solicitud = SolicitudPagina.de(null, null, LIMITES);

    assertThat(solicitud).isEqualTo(new SolicitudPagina(0, 20));
  }

  @Test
  @DisplayName("EXT-01 · 012-AC-01 el desplazamiento es la página por el tamaño")
  void debe_calcular_el_desplazamiento_cuando_se_pide_otra_pagina() {
    var solicitud = SolicitudPagina.de(2, 25, LIMITES);

    assertThat(solicitud.desplazamiento()).isEqualTo(50);
  }

  @Test
  @DisplayName("REQ-19 · 012-AC-03 un tamaño fuera de rango se rechaza y dice cuál es el rango")
  void debe_rechazar_el_tamano_cuando_esta_fuera_del_rango_permitido() {
    assertThatThrownBy(() -> SolicitudPagina.de(0, 0, LIMITES))
        .isInstanceOf(ParametroInvalido.class)
        .hasMessageContaining("size")
        .hasMessageContaining("entre 1 y 100");
    assertThatThrownBy(() -> SolicitudPagina.de(0, 101, LIMITES))
        .isInstanceOf(ParametroInvalido.class)
        .hasMessageContaining("size");
  }

  @Test
  @DisplayName("REQ-19 · 012-AC-03 una página negativa se rechaza")
  void debe_rechazar_la_pagina_cuando_es_negativa() {
    assertThatThrownBy(() -> SolicitudPagina.de(-1, 20, LIMITES))
        .isInstanceOf(ParametroInvalido.class)
        .hasMessageContaining("page")
        .hasMessageContaining("0 o mayor");
  }

  @Test
  @DisplayName("EXT-01 · 012-AC-01 el total de páginas redondea hacia arriba")
  void debe_redondear_hacia_arriba_el_total_de_paginas() {
    assertThat(new Pagina<>(List.of(), 0, 20, 45).totalPaginas()).isEqualTo(3);
    assertThat(new Pagina<>(List.of(), 0, 20, 40).totalPaginas()).isEqualTo(2);
    assertThat(new Pagina<>(List.of(), 0, 20, 0).totalPaginas()).isZero();
  }
}
