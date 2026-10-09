package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.inventario.ContenedoresCompartidos;
import com.roshka.challenge.inventario.NombresUnicos;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@ImportTestcontainers(ContenedoresCompartidos.class)
class AjusteStockApiIT {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  @Test
  @DisplayName("EXT-03 · 014-AC-01 subir lo disponible devuelve el stock y el movimiento")
  void debe_subir_el_stock_y_devolver_el_movimiento_cuando_se_fija_un_valor_mayor() {
    var id = producto(5, 0);

    var resultado = fijar(id, claveNueva(), 15, "reposición semanal");

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.productId").isEqualTo(id.toString());
    assertThat(resultado).bodyJson().extractingPath("$.total").isEqualTo(15);
    assertThat(resultado).bodyJson().extractingPath("$.available").isEqualTo(15);
    assertThat(resultado).bodyJson().extractingPath("$.reserved").isEqualTo(0);
    assertThat(resultado).bodyJson().extractingPath("$.movement.type").isEqualTo("ADD");
    assertThat(resultado).bodyJson().extractingPath("$.movement.quantity").isEqualTo(10);
    assertThat(resultado)
        .bodyJson()
        .extractingPath("$.movement.reason")
        .isEqualTo("reposición semanal");
    assertThat(resultado).bodyJson().extractingPath("$.movement.totalAfter").isEqualTo(15);
    assertThat(resultado).bodyJson().extractingPath("$.movement.movementId").isNotNull();
    assertThat(movimientosDe(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-02 bajar lo disponible no toca lo reservado")
  void debe_conservar_lo_reservado_cuando_baja_lo_disponible() {
    var id = producto(8, 2);

    var resultado = fijar(id, claveNueva(), 5, null);

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.total").isEqualTo(7);
    assertThat(resultado).bodyJson().extractingPath("$.available").isEqualTo(5);
    assertThat(resultado).bodyJson().extractingPath("$.reserved").isEqualTo(2);
    assertThat(resultado).bodyJson().extractingPath("$.movement.type").isEqualTo("REMOVE");
    assertThat(resultado).bodyJson().extractingPath("$.movement.quantity").isEqualTo(3);
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-03 se puede dejar en cero sin tocar las reservas")
  void debe_dejar_lo_disponible_en_cero_cuando_hay_reservas() {
    var id = producto(3, 5);

    var resultado = fijar(id, claveNueva(), 0, null);

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(stockDe(id)).containsExactly(5, 0, 5);
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-04 superar el total máximo es 409")
  void debe_responder_conflicto_cuando_el_total_superaria_el_maximo() {
    var id = producto(1, 5);

    var resultado = fijar(id, claveNueva(), 10_000_000, null);

    assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("STOCK_LIMIT_EXCEEDED");
    assertThat(stockDe(id)).containsExactly(6, 1, 5);
    assertThat(movimientosDe(id)).isZero();
  }

  @Test
  @DisplayName("REQ-19 · 014-AC-05 los valores inválidos son 400 o 404 y no registran movimientos")
  void debe_rechazar_el_pedido_cuando_es_invalido_sin_registrar_movimientos() {
    var id = producto(5, 0);

    var desconocido = fijar(UUID.randomUUID(), claveNueva(), 1, null);
    var negativo = fijar(id, claveNueva(), -1, null);
    var enorme = fijar(id, claveNueva(), 10_000_001, null);
    var motivoLargo = fijar(id, claveNueva(), 1, "x".repeat(201));
    var sinValor = enviar(id, claveNueva(), "{\"reason\":\"hola\"}");
    var propiedadExtra =
        enviar(id, claveNueva(), "{\"available\":1,\"total\":9,\"productId\":\"" + id + "\"}");

    assertThat(desconocido).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(desconocido).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
    for (var invalido : List.of(negativo, enorme, motivoLargo, sinValor, propiedadExtra)) {
      assertThat(invalido).hasStatus(HttpStatus.BAD_REQUEST);
      assertThat(invalido).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }
    assertThat(negativo)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .contains("available")
        .contains("entre 0 y 10000000");
    assertThat(propiedadExtra)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .contains("Propiedad desconocida");
    assertThat(movimientosDe(id)).isZero();
    assertThat(stockDe(id)).containsExactly(5, 5, 0);
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-06 si el valor ya era ese no hay movimiento")
  void debe_responder_sin_movimiento_cuando_lo_disponible_no_cambia() {
    var id = producto(5, 2);

    var resultado = fijar(id, claveNueva(), 5, null);

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.available").isEqualTo(5);
    assertThat(resultado).bodyJson().extractingPath("$.movement").isNull();
    assertThat(movimientosDe(id)).isZero();
  }

  @Test
  @DisplayName(
      "REQ-24 · 014-AC-07 repetir la misma clave y cuerpo reproduce la respuesta sin duplicar")
  void debe_reproducir_la_respuesta_y_registrar_un_solo_movimiento_cuando_se_repite_la_clave()
      throws Exception {
    var id = producto(5, 0);
    var clave = claveNueva();

    var primero = fijar(id, clave, 15, null);
    var segundo = fijar(id, clave, 15, null);

    assertThat(primero).hasStatus(HttpStatus.OK);
    assertThat(segundo).hasStatus(HttpStatus.OK);
    assertThat(segundo).headers().hasValue("Idempotent-Replayed", "true");
    var idOriginal =
        json.readTree(primero.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("movement")
            .get("movementId")
            .asString();
    assertThat(segundo).bodyJson().extractingPath("$.movement.movementId").isEqualTo(idOriginal);
    assertThat(stockDe(id)).containsExactly(15, 15, 0);
    assertThat(movimientosDe(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("REQ-24 · 014-AC-07 la misma clave con otro cuerpo es 422 y sin clave es 400")
  void debe_rechazar_la_clave_reutilizada_con_otro_cuerpo_y_la_ausencia_de_clave() {
    var id = producto(5, 0);
    var clave = claveNueva();
    fijar(id, clave, 15, null);

    var reutilizada = fijar(id, clave, 16, null);
    var sinClave =
        mvc.put()
            .uri("/products/{id}/stock", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"available\":1}")
            .exchange();

    assertThat(reutilizada).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(reutilizada).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(sinClave).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(sinClave).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_MISSING");
    assertThat(movimientosDe(id)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "EXT-03 · 014-AC-10 el historial se pagina con los movimientos más recientes primero")
  void debe_listar_el_historial_paginado_con_los_mas_recientes_primero() {
    var id = producto(1, 0);
    fijar(id, claveNueva(), 2, "primero");
    fijar(id, claveNueva(), 4, "segundo");
    fijar(id, claveNueva(), 1, "tercero");

    var pagina = mvc.get().uri("/products/{id}/stock/movements?page=0&size=2", id).exchange();
    var ultima = mvc.get().uri("/products/{id}/stock/movements?page=1&size=2", id).exchange();

    assertThat(pagina).hasStatus(HttpStatus.OK);
    assertThat(pagina).bodyJson().extractingPath("$.items.length()").isEqualTo(2);
    assertThat(pagina).bodyJson().extractingPath("$.items[0].reason").isEqualTo("tercero");
    assertThat(pagina).bodyJson().extractingPath("$.items[0].type").isEqualTo("REMOVE");
    assertThat(pagina).bodyJson().extractingPath("$.items[0].quantity").isEqualTo(3);
    assertThat(pagina).bodyJson().extractingPath("$.items[0].totalAfter").isEqualTo(1);
    assertThat(pagina).bodyJson().extractingPath("$.items[0].availableAfter").isEqualTo(1);
    assertThat(pagina).bodyJson().extractingPath("$.items[1].reason").isEqualTo("segundo");
    assertThat(pagina).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
    assertThat(pagina).bodyJson().extractingPath("$.totalPages").isEqualTo(2);
    assertThat(ultima).bodyJson().extractingPath("$.items.length()").isEqualTo(1);
    assertThat(ultima).bodyJson().extractingPath("$.items[0].reason").isEqualTo("primero");
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-05 el historial de un producto desconocido es 404")
  void debe_responder_no_encontrado_cuando_el_producto_del_historial_no_existe() {
    var resultado = mvc.get().uri("/products/{id}/stock/movements", UUID.randomUUID()).exchange();

    assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
  }

  @Test
  @DisplayName("EXT-03 · 014-RN-11 crear un producto con stock inicial no genera movimientos")
  void debe_tener_el_historial_vacio_cuando_el_producto_se_acaba_de_crear() {
    var creado =
        mvc.post()
            .uri("/products")
            .header("Idempotency-Key", claveNueva())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"name\":\"" + NombresUnicos.de("sin-historial") + "\",\"initialQuantity\":9}")
            .exchange();
    var id = json.readTree(contenido(creado)).get("productId").asString();

    var historial = mvc.get().uri("/products/{id}/stock/movements", id).exchange();

    assertThat(historial).hasStatus(HttpStatus.OK);
    assertThat(historial).bodyJson().extractingPath("$.totalElements").isEqualTo(0);
  }

  @Test
  @DisplayName(
      "REQ-24 · 014 un 409 por el tope no se guarda: reintentar con la misma clave lo reevalúa")
  void debe_reevaluar_el_pedido_cuando_se_reintenta_con_la_misma_clave_tras_un_409() {
    var id = producto(1, 5);
    var clave = claveNueva();
    var rechazado = fijar(id, clave, 10_000_000, null);
    jdbc.sql("update products set available = 6, reserved = 0 where id = :id")
        .param("id", id)
        .update();

    var reintento = fijar(id, clave, 10_000_000, null);

    assertThat(rechazado).hasStatus(HttpStatus.CONFLICT);
    assertThat(reintento).hasStatus(HttpStatus.OK);
    assertThat(reintento.getResponse().getHeader("Idempotent-Replayed")).isNull();
    assertThat(stockDe(id)).containsExactly(10_000_000, 10_000_000, 0);
  }

  @Test
  @DisplayName("REQ-19 · 014 el error por falta de available nombra el campo del contrato")
  void debe_nombrar_available_cuando_el_cuerpo_no_trae_el_valor() {
    var resultado = enviar(producto(1, 0), claveNueva(), "{\"reason\":\"hola\"}");

    assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(resultado)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .startsWith("available ")
        .doesNotContain("disponible");
  }

  private UUID producto(int disponible, int reservado) {
    var id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into products (id, name, total, available, reserved, created_at)
            values (:id, :name, :total, :available, :reserved, now())
            """)
        .param("id", id)
        .param("name", NombresUnicos.de("ajuste"))
        .param("total", disponible + reservado)
        .param("available", disponible)
        .param("reserved", reservado)
        .update();
    return id;
  }

  private String contenido(MvcTestResult resultado) {
    try {
      return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private MvcTestResult fijar(UUID id, String clave, int disponible, String motivo) {
    var cuerpo =
        "{\"available\":%d%s}"
            .formatted(disponible, motivo == null ? "" : ",\"reason\":\"" + motivo + "\"");
    return enviar(id, clave, cuerpo);
  }

  private MvcTestResult enviar(UUID id, String clave, String cuerpo) {
    return mvc.put()
        .uri("/products/{id}/stock", id)
        .header("Idempotency-Key", clave)
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo)
        .exchange();
  }

  private int movimientosDe(UUID id) {
    return jdbc.sql("select count(*) from stock_movements where product_id = :id")
        .param("id", id)
        .query(Integer.class)
        .single();
  }

  /** total, disponible y reservado. */
  private List<Integer> stockDe(UUID id) {
    return jdbc.sql("select total, available, reserved from products where id = :id")
        .param("id", id)
        .query(
            (rs, n) -> List.of(rs.getInt("total"), rs.getInt("available"), rs.getInt("reserved")))
        .single();
  }

  private static String claveNueva() {
    return UUID.randomUUID().toString();
  }
}
