package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.inventario.ContenedoresCompartidos;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La documentación OpenAPI de ms-inventario no puede desviarse de la API: cada operación, cada
 * código de estado y cada {@code code} de error documentados están acá, y un cambio en la API
 * obliga a actualizar la tabla.
 */
@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@ImportTestcontainers(ContenedoresCompartidos.class)
class ContratoOpenApiIT {

  /** Estado exitoso y errores esperados por estado (estado → códigos). */
  record Esperado(int exito, Map<Integer, Set<String>> errores, boolean escritura) {}

  static final Set<String> CLAVE = Set.of("IDEMPOTENCY_KEY_MISSING", "IDEMPOTENCY_KEY_INVALID");

  static final Map<String, Esperado> OPERACIONES =
      Map.of(
          "POST /products",
          new Esperado(
              201,
              Map.of(
                  400, union(CLAVE, "VALIDATION_FAILED"),
                  409, Set.of("PRODUCT_ALREADY_EXISTS", "IDEMPOTENCY_KEY_IN_PROGRESS"),
                  422, Set.of("IDEMPOTENCY_KEY_REUSED"),
                  503, Set.of("LOCK_UNAVAILABLE")),
              true),
          "GET /products",
          new Esperado(200, Map.of(400, Set.of("VALIDATION_FAILED")), false),
          "GET /products/{productId}",
          new Esperado(
              200,
              Map.of(400, Set.of("VALIDATION_FAILED"), 404, Set.of("PRODUCT_NOT_FOUND")),
              false),
          "GET /products/{productId}/stock",
          new Esperado(
              200,
              Map.of(400, Set.of("VALIDATION_FAILED"), 404, Set.of("PRODUCT_NOT_FOUND")),
              false),
          "PUT /products/{productId}/stock",
          new Esperado(
              200,
              Map.of(
                  400, union(CLAVE, "VALIDATION_FAILED"),
                  404, Set.of("PRODUCT_NOT_FOUND"),
                  409, Set.of("STOCK_LIMIT_EXCEEDED", "IDEMPOTENCY_KEY_IN_PROGRESS"),
                  422, Set.of("IDEMPOTENCY_KEY_REUSED"),
                  503, Set.of("LOCK_UNAVAILABLE")),
              true),
          "GET /products/{productId}/stock/movements",
          new Esperado(
              200,
              Map.of(400, Set.of("VALIDATION_FAILED"), 404, Set.of("PRODUCT_NOT_FOUND")),
              false));

  @Autowired MockMvcTester mvc;
  @Autowired JsonMapper json;

  private JsonNode documento;

  @BeforeEach
  void cargarDocumento() throws Exception {
    var respuesta = mvc.get().uri("/v3/api-docs").exchange().getResponse();
    documento = json.readTree(respuesta.getContentAsString(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("EXT-04 · 015-AC-01 la API documenta exactamente las operaciones esperadas")
  void debe_documentar_solo_las_operaciones_esperadas_cuando_se_lee_el_documento() {
    var documentadas = new TreeSet<String>();
    documento
        .get("paths")
        .properties()
        .forEach(
            ruta ->
                ruta.getValue()
                    .properties()
                    .forEach(
                        op -> documentadas.add(op.getKey().toUpperCase() + " " + ruta.getKey())));

    assertThat(documentadas).containsExactlyInAnyOrderElementsOf(OPERACIONES.keySet());
  }

  @Test
  @DisplayName("EXT-04 · 015-AC-01 cada operación tiene resumen, descripción y etiqueta")
  void debe_describir_cada_operacion_cuando_se_recorre_el_documento() {
    forEachOperacion(
        (clave, op) -> {
          assertThat(op.path("summary").asString("")).as(clave + " summary").isNotBlank();
          assertThat(op.path("description").asString("")).as(clave + " description").isNotBlank();
          assertThat(op.path("tags")).as(clave + " tags").isNotEmpty();
        });
  }

  @Test
  @DisplayName(
      "EXT-04 · 015-AC-01 la respuesta exitosa está tipada con un esquema, no es un string")
  void debe_tipar_la_respuesta_exitosa_cuando_se_recorre_el_documento() {
    forEachOperacion(
        (clave, op) -> {
          var exito = OPERACIONES.get(clave).exito();
          var contenido = op.path("responses").path(String.valueOf(exito)).path("content");
          assertThat(contenido.isMissingNode()).as(clave + " content").isFalse();
          var esquema = contenido.path("application/json").path("schema");
          assertThat(esquema.has("$ref")).as(clave + " esquema " + esquema).isTrue();
        });
  }

  @Test
  @DisplayName("REQ-19 · 015-AC-02 cada error documenta su code con el esquema y un ejemplo")
  void debe_documentar_cada_error_con_su_ejemplo_cuando_se_recorre_el_documento() {
    forEachOperacion(
        (clave, op) -> {
          var esperado = OPERACIONES.get(clave);
          var respuestas = op.path("responses");
          var estadosDeError = new TreeSet<Integer>();
          respuestas.propertyNames().stream()
              .filter(s -> !s.equals("default") && Integer.parseInt(s) >= 400)
              .forEach(s -> estadosDeError.add(Integer.parseInt(s)));
          assertThat(estadosDeError)
              .as(clave + " estados de error")
              .isEqualTo(new TreeSet<>(esperado.errores().keySet()));
          esperado
              .errores()
              .forEach(
                  (estado, codigos) -> {
                    var contenido =
                        respuestas
                            .path(String.valueOf(estado))
                            .path("content")
                            .path("application/problem+json");
                    assertThat(contenido.path("schema").has("$ref"))
                        .as(clave + " " + estado + " esquema")
                        .isTrue();
                    assertThat(codigosDeEjemplos(contenido))
                        .as(clave + " " + estado + " codes")
                        .isEqualTo(new TreeSet<>(codigos));
                  });
        });
  }

  @Test
  @DisplayName("REQ-24 · 015-AC-03 las escrituras exigen Idempotency-Key y documentan Retry-After")
  void debe_exigir_la_clave_de_idempotencia_cuando_la_operacion_escribe() {
    forEachOperacion(
        (clave, op) -> {
          var cabeceras = new ArrayList<JsonNode>();
          op.path("parameters")
              .forEach(
                  p -> {
                    if ("Idempotency-Key".equals(p.path("name").asString())) {
                      cabeceras.add(p);
                    }
                  });
          if (OPERACIONES.get(clave).escritura()) {
            assertThat(cabeceras).as(clave + " Idempotency-Key").hasSize(1);
            assertThat(cabeceras.getFirst().path("required").asBoolean()).isTrue();
            assertThat(cabeceras.getFirst().path("in").asString()).isEqualTo("header");
            var conflicto = op.path("responses").path("409").path("headers").path("Retry-After");
            assertThat(conflicto.isMissingNode()).as(clave + " Retry-After en 409").isFalse();
          } else {
            assertThat(cabeceras).as(clave + " no exige clave").isEmpty();
          }
        });
  }

  @Test
  @DisplayName("EXT-01 · 015-AC-04 los listados documentan page y size con defecto y límites")
  void debe_documentar_la_paginacion_cuando_el_listado_es_paginado() {
    for (var clave : List.of("GET /products", "GET /products/{productId}/stock/movements")) {
      var op = operacion(clave);
      var parametros = new TreeMap<String, JsonNode>();
      op.path("parameters").forEach(p -> parametros.put(p.path("name").asString(), p));
      assertThat(parametros).as(clave).containsKeys("page", "size");
      var page = parametros.get("page");
      var size = parametros.get("size");
      assertThat(page.path("required").asBoolean(false)).isFalse();
      assertThat(page.path("schema").path("default").asInt(-1)).isEqualTo(0);
      assertThat(page.path("schema").path("minimum").asInt(-1)).isEqualTo(0);
      assertThat(size.path("schema").path("default").asInt(-1)).isEqualTo(20);
      assertThat(size.path("schema").path("minimum").asInt(-1)).isEqualTo(1);
      assertThat(size.path("schema").path("maximum").asInt(-1)).isEqualTo(100);
      assertThat(page.path("description").asString("")).isNotBlank();
      assertThat(size.path("description").asString("")).isNotBlank();
    }
  }

  @Test
  @DisplayName(
      "EXT-04 · 015-AC-05 el documento tiene título, descripción y el gateway como servidor")
  void debe_apuntar_al_gateway_cuando_se_lee_la_informacion_general() {
    assertThat(documento.path("info").path("title").asString("")).isNotBlank();
    assertThat(documento.path("info").path("description").asString("")).isNotBlank();
    assertThat(documento.path("servers").path(0).path("url").asString()).isEqualTo("/");
  }

  private void forEachOperacion(java.util.function.BiConsumer<String, JsonNode> accion) {
    OPERACIONES.keySet().forEach(clave -> accion.accept(clave, operacion(clave)));
  }

  private JsonNode operacion(String clave) {
    var partes = clave.split(" ", 2);
    return documento.path("paths").path(partes[1]).path(partes[0].toLowerCase());
  }

  private Set<String> codigosDeEjemplos(JsonNode contenido) {
    var codigos = new TreeSet<String>();
    contenido
        .path("examples")
        .forEach(
            ejemplo -> {
              var valor = ejemplo.path("value");
              var nodo = valor.isString() ? json.readTree(valor.asString()) : valor;
              codigos.add(nodo.path("code").asString(""));
            });
    return codigos;
  }

  static Set<String> union(Set<String> base, String... extra) {
    var resultado = new TreeSet<>(base);
    resultado.addAll(Set.of(extra));
    return resultado;
  }
}
