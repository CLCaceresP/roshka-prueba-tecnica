package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.inventario.ContenedoresCompartidos;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.RegistrarProducto;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@ImportTestcontainers(ContenedoresCompartidos.class)
class ProductoListadoIT {

  private static final int PRODUCTOS = 45;

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired RegistrarProducto registrarProducto;
  @Autowired JsonMapper json;

  @AfterEach
  void limpiarAlTerminar() {
    vaciarCatalogo();
  }

  @BeforeEach
  void vaciarCatalogo() {
    jdbc.sql("delete from stock_movements").update();
    jdbc.sql("delete from reservation_lines").update();
    jdbc.sql("delete from reservations").update();
    jdbc.sql("delete from products").update();
  }

  @Test
  @DisplayName(
      "EXT-01 · 012-AC-01 el listado devuelve una página con sus totales y orden por nombre")
  void debe_devolver_la_pagina_con_los_totales_cuando_hay_varias_paginas() {
    cargar(PRODUCTOS);

    var resultado = mvc.get().uri("/products?page=1&size=20").exchange();

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.items.length()").isEqualTo(20);
    assertThat(resultado).bodyJson().extractingPath("$.page").isEqualTo(1);
    assertThat(resultado).bodyJson().extractingPath("$.size").isEqualTo(20);
    assertThat(resultado).bodyJson().extractingPath("$.totalElements").isEqualTo(PRODUCTOS);
    assertThat(resultado).bodyJson().extractingPath("$.totalPages").isEqualTo(3);
    assertThat(resultado).bodyJson().extractingPath("$.items[0].name").isEqualTo("producto-20");
    assertThat(resultado).bodyJson().extractingPath("$.items[0].available").isEqualTo(1);
  }

  @Test
  @DisplayName("EXT-01 · 012-AC-02 sin parámetros devuelve la página 0 con tamaño 20")
  void debe_usar_la_primera_pagina_con_tamano_veinte_cuando_no_hay_parametros() {
    cargar(25);

    var resultado = mvc.get().uri("/products").exchange();

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.page").isEqualTo(0);
    assertThat(resultado).bodyJson().extractingPath("$.size").isEqualTo(20);
    assertThat(resultado).bodyJson().extractingPath("$.items.length()").isEqualTo(20);
  }

  @Test
  @DisplayName("REQ-19 · 012-AC-03 los parámetros inválidos son 400 y el detalle dice el rango")
  void debe_rechazar_la_consulta_cuando_los_parametros_de_paginacion_son_invalidos() {
    var tamanoCero = mvc.get().uri("/products?size=0").exchange();
    var tamanoGrande = mvc.get().uri("/products?size=101").exchange();
    var paginaNegativa = mvc.get().uri("/products?page=-1").exchange();
    var noNumerico = mvc.get().uri("/products?size=abc").exchange();

    assertThat(tamanoCero).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(tamanoCero).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(tamanoCero)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .contains("size")
        .contains("entre 1 y 100");
    assertThat(tamanoGrande).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(paginaNegativa).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(paginaNegativa).bodyJson().extractingPath("$.detail").asString().contains("page");
    assertThat(noNumerico).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(noNumerico).bodyJson().extractingPath("$.detail").asString().contains("size");
  }

  @Test
  @DisplayName("EXT-01 · 012-AC-04 una página fuera de rango no es un error")
  void debe_devolver_items_vacios_cuando_la_pagina_esta_fuera_de_rango() {
    cargar(3);

    var resultado = mvc.get().uri("/products?page=5").exchange();

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.items.length()").isEqualTo(0);
    assertThat(resultado).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
    assertThat(resultado).bodyJson().extractingPath("$.totalPages").isEqualTo(1);
  }

  @Test
  @DisplayName("EXT-01 · 012-AC-04 sin productos el listado está vacío")
  void debe_devolver_cero_paginas_cuando_no_hay_productos() {
    var resultado = mvc.get().uri("/products").exchange();

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.items.length()").isEqualTo(0);
    assertThat(resultado).bodyJson().extractingPath("$.totalElements").isEqualTo(0);
    assertThat(resultado).bodyJson().extractingPath("$.totalPages").isEqualTo(0);
  }

  @Test
  @DisplayName("EXT-01 · 012-AC-05 recorrer todas las páginas devuelve cada producto una sola vez")
  void debe_devolver_cada_producto_una_vez_cuando_se_recorren_todas_las_paginas() throws Exception {
    registrarProducto.registrar("Beta", 1);
    registrarProducto.registrar("alfa", 1);
    registrarProducto.registrar("Alfa dos", 1);
    registrarProducto.registrar("GAMMA", 1);
    registrarProducto.registrar("delta", 1);
    var recorridos = new ArrayList<String>();

    for (int pagina = 0; pagina < 3; pagina++) {
      var resultado = mvc.get().uri("/products?page={p}&size=2", pagina).exchange();
      assertThat(resultado).hasStatus(HttpStatus.OK);
      json.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
          .get("items")
          .forEach(item -> recorridos.add(item.get("name").asString()));
    }

    assertThat(recorridos).containsExactly("alfa", "Alfa dos", "Beta", "delta", "GAMMA");
  }

  @Test
  @DisplayName(
      "EXT-01 · 012-AC-06 consultar un producto devuelve nombre y stock; desconocido es 404")
  void debe_devolver_el_producto_cuando_existe_y_404_cuando_no() {
    var producto = registrarProducto.registrar("Consultado", 7);

    var existente = mvc.get().uri("/products/{id}", producto.id()).exchange();
    var desconocido = mvc.get().uri("/products/{id}", UUID.randomUUID()).exchange();

    assertThat(existente).hasStatus(HttpStatus.OK);
    assertThat(existente)
        .bodyJson()
        .extractingPath("$.productId")
        .isEqualTo(producto.id().toString());
    assertThat(existente).bodyJson().extractingPath("$.name").isEqualTo("Consultado");
    assertThat(existente).bodyJson().extractingPath("$.total").isEqualTo(7);
    assertThat(existente).bodyJson().extractingPath("$.available").isEqualTo(7);
    assertThat(existente).bodyJson().extractingPath("$.reserved").isEqualTo(0);
    assertThat(desconocido).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(desconocido).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
  }

  private void cargar(int cantidad) {
    for (int i = 0; i < cantidad; i++) {
      registrarProducto.registrar("producto-%02d".formatted(i), 1);
    }
  }
}
