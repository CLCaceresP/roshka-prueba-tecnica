package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.pedidos.ContenedoresCompartidos;
import com.roshka.challenge.pedidos.aplicacion.consulta.ConfiguracionConsulta;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "saga.timeout.intervalo-verificacion=1h")
@DirtiesContext
@AutoConfigureMockMvc
@ImportTestcontainers(ContenedoresCompartidos.class)
class PedidoListadoIT {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired PedidoRepository pedidos;
  @Autowired TransactionTemplate tx;
  @Autowired JsonMapper json;
  @Autowired ConfiguracionConsulta consulta;

  @AfterEach
  void limpiarAlTerminar() {
    vaciarPedidos();
  }

  @BeforeEach
  void vaciarPedidos() {
    jdbc.sql("delete from order_lines").update();
    jdbc.sql("delete from orders").update();
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-01 se listan los pedidos de los días del rango, completos")
  void debe_listar_los_pedidos_de_los_dias_del_rango_cuando_hay_pedidos_de_otros_dias()
      throws Exception {
    pedidoEn(2026, 10, 8, 12, 0, 0);
    var inicioDelRango = pedidoEn(2026, 10, 9, 0, 0, 0);
    var finDelDia9 = pedidoEn(2026, 10, 9, 23, 59, 59);
    var dia10 = pedidoEn(2026, 10, 10, 12, 0, 0);
    pedidoEn(2026, 10, 11, 0, 0, 0);

    var resultado = mvc.get().uri("/orders?from=10/09/2026&to=10/10/2026").exchange();

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(idsDe(resultado))
        .containsExactly(
            dia10.id().toString(), finDelDia9.id().toString(), inicioDelRango.id().toString());
    assertThat(resultado).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-02 sin fechas se listan solo los pedidos de hoy")
  void debe_listar_solo_los_pedidos_de_hoy_cuando_no_se_indican_fechas() throws Exception {
    var hoy = LocalDate.now(consulta.zona());
    var deHoy = pedidoEn(hoy.atTime(0, 0, 1));
    pedidoEn(hoy.minusDays(1).atTime(12, 0));

    var sinFechas = mvc.get().uri("/orders").exchange();
    var soloDesde = mvc.get().uri("/orders?from={f}", formato(hoy)).exchange();
    var soloHasta = mvc.get().uri("/orders?to={t}", formato(hoy)).exchange();

    assertThat(idsDe(sinFechas)).containsExactly(deHoy.id().toString());
    assertThat(idsDe(soloDesde)).containsExactly(deHoy.id().toString());
    assertThat(idsDe(soloHasta)).containsExactly(deHoy.id().toString());
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-03 la última página trae el resto y los totales")
  void debe_paginar_con_totales_cuando_hay_mas_pedidos_que_el_tamano() throws Exception {
    for (int i = 0; i < 45; i++) {
      pedidoEn(2026, 10, 10, 12, 0, i);
    }

    var resultado =
        mvc.get().uri("/orders?from=10/10/2026&to=10/10/2026&page=2&size=20").exchange();

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.items.length()").isEqualTo(5);
    assertThat(resultado).bodyJson().extractingPath("$.page").isEqualTo(2);
    assertThat(resultado).bodyJson().extractingPath("$.size").isEqualTo(20);
    assertThat(resultado).bodyJson().extractingPath("$.totalElements").isEqualTo(45);
    assertThat(resultado).bodyJson().extractingPath("$.totalPages").isEqualTo(3);
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-04 con la misma fecha de creación el orden es estable")
  void debe_devolver_cada_pedido_una_vez_cuando_varios_tienen_la_misma_fecha() throws Exception {
    var creados = new ArrayList<String>();
    for (int i = 0; i < 5; i++) {
      creados.add(pedidoEn(2026, 10, 10, 12, 0, 0).id().toString());
    }
    var recorridos = new ArrayList<String>();

    for (int pagina = 0; pagina < 3; pagina++) {
      var resultado = mvc.get().uri("/orders?from=10/10/2026&page={p}&size=2", pagina).exchange();
      recorridos.addAll(idsDe(resultado));
    }

    assertThat(recorridos).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(creados);
    assertThat(recorridos).isSortedAccordingTo(java.util.Comparator.reverseOrder());
  }

  @Test
  @DisplayName("REQ-19 · 013-AC-05 los parámetros inválidos son 400 y el detalle dice cuál")
  void debe_rechazar_la_consulta_cuando_los_parametros_son_invalidos() throws Exception {
    var formatoIso = mvc.get().uri("/orders?from=2026-10-01").exchange();
    var fechaInexistente = mvc.get().uri("/orders?from=13/45/2026").exchange();
    var invertido = mvc.get().uri("/orders?from=10/10/2026&to=10/09/2026").exchange();
    var demasiadosDias = mvc.get().uri("/orders?from=10/01/2026&to=11/01/2026").exchange();
    var tamanoGrande = mvc.get().uri("/orders?size=101").exchange();
    var paginaNegativa = mvc.get().uri("/orders?page=-1").exchange();
    var tamanoNoNumerico = mvc.get().uri("/orders?size=abc").exchange();

    assertThat(formatoIso).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(formatoIso).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(detalleDe(formatoIso)).contains("from").contains("MM/dd/yyyy");
    assertThat(detalleDe(fechaInexistente)).contains("from");
    assertThat(detalleDe(invertido)).contains("posterior");
    assertThat(detalleDe(demasiadosDias)).contains("31 días");
    assertThat(detalleDe(tamanoGrande)).contains("size");
    assertThat(detalleDe(paginaNegativa)).contains("page");
    assertThat(detalleDe(tamanoNoNumerico)).contains("size");
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-06 cada pedido del listado trae sus líneas y su estado")
  void debe_incluir_las_lineas_y_el_estado_de_cada_pedido() throws Exception {
    var pedido = pedidoEn(2026, 10, 10, 12, 0, 0);

    var resultado = mvc.get().uri("/orders?from=10/10/2026").exchange();

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado)
        .bodyJson()
        .extractingPath("$.items[0].orderId")
        .isEqualTo(pedido.id().toString());
    assertThat(resultado).bodyJson().extractingPath("$.items[0].status").isEqualTo("PENDING");
    assertThat(resultado).bodyJson().extractingPath("$.items[0].lines.length()").isEqualTo(2);
    assertThat(resultado)
        .bodyJson()
        .extractingPath("$.items[0].createdAt")
        .isEqualTo(pedido.creadoEn().toString());
    assertThat(resultado)
        .bodyJson()
        .extractingPath("$.items[0].updatedAt")
        .isEqualTo(pedido.actualizadoEn().toString());
    assertThat(resultado).bodyJson().extractingPath("$.items[0].rejectionReason").isNull();
    assertThat(resultado)
        .bodyJson()
        .extractingPath("$.items[0].lines[*].quantity")
        .asList()
        .containsExactlyInAnyOrder(1, 2);
    assertThat(resultado)
        .bodyJson()
        .extractingPath("$.items[0].lines[*].productId")
        .asList()
        .containsExactlyInAnyOrderElementsOf(
            pedido.lineas().stream().map(l -> l.idProducto().toString()).toList());
  }

  @Test
  @DisplayName("EXT-02 · 013-AC-07 un rango sin pedidos devuelve una página vacía")
  void debe_devolver_items_vacios_cuando_no_hay_pedidos_en_el_rango() {
    var resultado = mvc.get().uri("/orders?from=01/01/2026&to=01/02/2026").exchange();

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.items.length()").isEqualTo(0);
    assertThat(resultado).bodyJson().extractingPath("$.totalElements").isEqualTo(0);
    assertThat(resultado).bodyJson().extractingPath("$.totalPages").isEqualTo(0);
  }

  @Test
  @DisplayName("REQ-16 · 013-AC-08 existe un índice por fecha de creación")
  void debe_tener_el_indice_por_fecha_cuando_se_migra_la_base() {
    var indices =
        jdbc.sql("select indexdef from pg_indexes where tablename = 'orders'")
            .query(String.class)
            .list();

    assertThat(indices).anyMatch(d -> d.contains("created_at DESC, id DESC"));
  }

  private Pedido pedidoEn(int anio, int mes, int dia, int hora, int minuto, int segundo) {
    return pedidoEn(LocalDateTime.of(anio, mes, dia, hora, minuto, segundo));
  }

  private Pedido pedidoEn(LocalDateTime fecha) {
    Instant instante = fecha.atZone(consulta.zona()).toInstant();
    var pedido =
        Pedido.colocar(
            UUID.randomUUID(),
            List.of(new LineaPedido(UUID.randomUUID(), 1), new LineaPedido(UUID.randomUUID(), 2)),
            "corr-" + UUID.randomUUID(),
            instante);
    pedido.extraerEventos();
    tx.executeWithoutResult(s -> pedidos.insertar(pedido));
    return pedido;
  }

  private List<String> idsDe(org.springframework.test.web.servlet.assertj.MvcTestResult resultado)
      throws Exception {
    assertThat(resultado).hasStatus(HttpStatus.OK);
    var ids = new ArrayList<String>();
    json.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .get("items")
        .forEach(item -> ids.add(item.get("orderId").asString()));
    return ids;
  }

  private String detalleDe(org.springframework.test.web.servlet.assertj.MvcTestResult resultado)
      throws Exception {
    assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
    return json.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .get("detail")
        .asString();
  }

  private static String formato(LocalDate fecha) {
    return "%02d/%02d/%d".formatted(fecha.getMonthValue(), fecha.getDayOfMonth(), fecha.getYear());
  }
}
