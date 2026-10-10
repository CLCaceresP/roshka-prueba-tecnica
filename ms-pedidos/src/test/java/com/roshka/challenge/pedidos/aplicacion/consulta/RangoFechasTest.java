package com.roshka.challenge.pedidos.aplicacion.consulta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.pedidos.dominio.ParametroInvalido;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RangoFechasTest {

  private static final ZoneId ZONA = ZoneOffset.ofHours(-3);
  private static final ConfiguracionConsulta CONFIGURACION = new ConfiguracionConsulta(ZONA, 31);
  private static final Clock RELOJ =
      Clock.fixed(Instant.parse("2026-10-10T01:30:00Z"), ZoneOffset.UTC);

  @Test
  @DisplayName("EXT-02 · 013-AC-01 el rango abarca días completos en la zona configurada")
  void debe_abarcar_desde_la_medianoche_del_primer_dia_hasta_la_del_dia_siguiente_al_ultimo() {
    var rango = RangoFechas.de(fecha(2026, 10, 9), fecha(2026, 10, 10), CONFIGURACION, RELOJ);

    assertThat(rango.instanteDesde()).isEqualTo(Instant.parse("2026-10-09T03:00:00Z"));
    assertThat(rango.instanteHasta()).isEqualTo(Instant.parse("2026-10-11T03:00:00Z"));
    assertThat(rango.descripcion()).isEqualTo("del 10/09/2026 al 10/10/2026");
  }

  @Test
  @DisplayName(
      "EXT-02 · 013-AC-01 el día se mide en la zona aunque el cambio de horario dure 23 o 25 horas")
  void debe_usar_la_medianoche_local_cuando_el_dia_tiene_23_o_25_horas() {
    var nuevaYork = new ConfiguracionConsulta(ZoneId.of("America/New_York"), 31);

    var primavera = RangoFechas.de(fecha(2026, 3, 8), fecha(2026, 3, 8), nuevaYork, RELOJ);
    var otonio = RangoFechas.de(fecha(2026, 11, 1), fecha(2026, 11, 1), nuevaYork, RELOJ);

    assertThat(primavera.instanteHasta()).isEqualTo(Instant.parse("2026-03-09T04:00:00Z"));
    assertThat(primavera.instanteDesde()).isEqualTo(Instant.parse("2026-03-08T05:00:00Z"));
    assertThat(otonio.instanteHasta()).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"));
    assertThat(otonio.instanteDesde()).isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-02 sin fechas se usa el día de hoy en la zona configurada")
  void debe_usar_el_dia_de_hoy_cuando_no_se_indican_fechas() {
    var rango = RangoFechas.de(null, null, CONFIGURACION, RELOJ);

    assertThat(rango.instanteDesde()).isEqualTo(Instant.parse("2026-10-09T03:00:00Z"));
    assertThat(rango.instanteHasta()).isEqualTo(Instant.parse("2026-10-10T03:00:00Z"));
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-02 con una sola fecha el rango es ese día")
  void debe_usar_ese_dia_cuando_solo_se_indica_una_fecha() {
    var soloDesde = RangoFechas.de(fecha(2026, 10, 5), null, CONFIGURACION, RELOJ);
    var soloHasta = RangoFechas.de(null, fecha(2026, 10, 7), CONFIGURACION, RELOJ);

    assertThat(soloDesde.descripcion()).isEqualTo("del 10/05/2026 al 10/05/2026");
    assertThat(soloHasta.descripcion()).isEqualTo("del 10/07/2026 al 10/07/2026");
  }

  @Test
  @DisplayName("REQ-19 · 013-AC-05 el inicio no puede ser posterior al fin")
  void debe_rechazar_el_rango_cuando_el_inicio_es_posterior_al_fin() {
    assertThatThrownBy(
            () -> RangoFechas.de(fecha(2026, 10, 10), fecha(2026, 10, 9), CONFIGURACION, RELOJ))
        .isInstanceOf(ParametroInvalido.class)
        .hasMessageContaining("from")
        .hasMessageContaining("posterior");
  }

  @Test
  @DisplayName("REQ-19 · 013-AC-05 el rango no puede superar el máximo de días")
  void debe_rechazar_el_rango_cuando_supera_el_maximo_de_dias() {
    var treintaYUno = RangoFechas.de(fecha(2026, 10, 1), fecha(2026, 10, 31), CONFIGURACION, RELOJ);

    assertThat(treintaYUno.descripcion()).isEqualTo("del 10/01/2026 al 10/31/2026");
    assertThatThrownBy(
            () -> RangoFechas.de(fecha(2026, 10, 1), fecha(2026, 11, 1), CONFIGURACION, RELOJ))
        .isInstanceOf(ParametroInvalido.class)
        .hasMessageContaining("31 días")
        .hasMessageContaining("32");
  }

  private static LocalDate fecha(int anio, int mes, int dia) {
    return LocalDate.of(anio, mes, dia);
  }
}
