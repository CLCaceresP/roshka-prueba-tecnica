package com.roshka.challenge.pedidos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.pedidos.aplicacion.consulta.ConfiguracionConsulta;
import com.roshka.challenge.pedidos.aplicacion.consulta.ConfiguracionPaginacion;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsultaPedidosTest {

  private static final Clock RELOJ =
      Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);

  private final PedidosEnMemoria pedidos = new PedidosEnMemoria();
  private final ConsultaPedidos consulta =
      new ConsultaPedidos(
          pedidos,
          new ConfiguracionConsulta(ZoneOffset.UTC, 31),
          new ConfiguracionPaginacion(20, 100),
          RELOJ);

  @Test
  @DisplayName("EXT-02 · 013-AC-01/02 sin fechas solo se listan los pedidos de hoy")
  void debe_listar_solo_los_pedidos_de_hoy_cuando_no_se_indican_fechas() {
    pedidoCreadoEn("2026-10-09T23:59:59Z");
    var hoyTemprano = pedidoCreadoEn("2026-10-10T00:00:00Z");
    var hoyTarde = pedidoCreadoEn("2026-10-10T23:59:59Z");
    pedidoCreadoEn("2026-10-11T00:00:00Z");

    var pagina = consulta.listar(null, null, null, null);

    assertThat(pagina.items())
        .extracting(Pedido::id)
        .containsExactly(hoyTarde.id(), hoyTemprano.id());
    assertThat(pagina.totalElementos()).isEqualTo(2);
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-01 un rango explícito incluye los dos días completos")
  void debe_incluir_los_dos_extremos_cuando_se_indica_un_rango() {
    var ayer = pedidoCreadoEn("2026-10-09T10:00:00Z");
    var hoy = pedidoCreadoEn("2026-10-10T10:00:00Z");
    pedidoCreadoEn("2026-10-08T23:59:59Z");

    var pagina = consulta.listar(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10), 0, 20);

    assertThat(pagina.items()).extracting(Pedido::id).containsExactly(hoy.id(), ayer.id());
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-03 la página trae el resto y el total")
  void debe_devolver_la_ultima_pagina_con_el_resto_cuando_el_total_no_es_multiplo() {
    for (int i = 0; i < 5; i++) {
      pedidoCreadoEn("2026-10-10T10:00:0%dZ".formatted(i));
    }

    var pagina = consulta.listar(null, null, 2, 2);

    assertThat(pagina.items()).hasSize(1);
    assertThat(pagina.totalElementos()).isEqualTo(5);
    assertThat(pagina.totalPaginas()).isEqualTo(3);
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-07 un rango sin pedidos devuelve una página vacía")
  void debe_devolver_items_vacios_cuando_no_hay_pedidos_en_el_rango() {
    var pagina = consulta.listar(null, null, null, null);

    assertThat(pagina.items()).isEmpty();
    assertThat(pagina.totalElementos()).isZero();
  }

  private Pedido pedidoCreadoEn(String instante) {
    var pedido =
        Pedido.colocar(
            UUID.randomUUID(),
            List.of(new LineaPedido(UUID.randomUUID(), 1)),
            "corr",
            Instant.parse(instante));
    pedido.extraerEventos();
    pedidos.insertar(pedido);
    return pedido;
  }
}
