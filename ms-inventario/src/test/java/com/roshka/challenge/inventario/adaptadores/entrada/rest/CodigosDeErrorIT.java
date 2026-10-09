package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.inventario.ContenedoresCompartidos;
import com.roshka.challenge.inventario.NombresUnicos;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Provoca cada error que el documento OpenAPI promete y comprueba que la API responde ese estado y
 * ese {@code code}. El 503 (Redis caído) lo prueba {@code BloqueoNoDisponibleIT}.
 */
@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@ImportTestcontainers(ContenedoresCompartidos.class)
class CodigosDeErrorIT {

  private record Caso(
      String operacion, int estado, String codigo, Supplier<MvcTestResult> ejecutar) {}

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired RedissonClient redisson;
  @Autowired JsonMapper json;

  @Test
  @DisplayName("REQ-19 REQ-24 · 015-AC-07 cada error documentado es el que la API devuelve")
  void debe_devolver_cada_error_documentado_cuando_se_provoca() throws Exception {
    var observados = new TreeSet<String>();

    for (var caso : casos()) {
      var resultado = caso.ejecutar().get();
      var respuesta = resultado.getResponse();
      var codigo =
          json.readTree(respuesta.getContentAsString(StandardCharsets.UTF_8))
              .get("code")
              .asString();
      assertThat(respuesta.getStatus())
          .as(caso.operacion() + " " + caso.codigo())
          .isEqualTo(caso.estado());
      assertThat(codigo).as(caso.operacion() + " " + caso.estado()).isEqualTo(caso.codigo());
      observados.add(caso.operacion() + " " + caso.estado() + " " + codigo);
    }

    var documentados = new TreeSet<String>();
    ContratoOpenApiIT.OPERACIONES.forEach(
        (operacion, esperado) ->
            esperado
                .errores()
                .forEach(
                    (estado, codigos) -> {
                      if (estado != 503) {
                        codigos.forEach(c -> documentados.add(operacion + " " + estado + " " + c));
                      }
                    }));
    assertThat(observados).isEqualTo(documentados);
  }

  private java.util.List<Caso> casos() {
    var casos = new ArrayList<Caso>();
    var invalido = "{\"name\":\"\",\"initialQuantity\":1}";
    casos.add(caso("POST /products", 400, "VALIDATION_FAILED", () -> crear(clave(), invalido)));
    casos.add(caso("POST /products", 400, "IDEMPOTENCY_KEY_MISSING", () -> crear(null, valido())));
    casos.add(
        caso(
            "POST /products",
            400,
            "IDEMPOTENCY_KEY_INVALID",
            () -> crear("clave con espacios", valido())));
    casos.add(
        caso(
            "POST /products",
            409,
            "PRODUCT_ALREADY_EXISTS",
            () -> {
              var nombre = NombresUnicos.de("repetido");
              crear(clave(), "{\"name\":\"" + nombre + "\",\"initialQuantity\":1}");
              return crear(clave(), "{\"name\":\"" + nombre + "\",\"initialQuantity\":2}");
            }));
    casos.add(
        caso(
            "POST /products",
            409,
            "IDEMPOTENCY_KEY_IN_PROGRESS",
            () -> conClaveRetenida(clave -> crear(clave, valido()))));
    casos.add(
        caso(
            "POST /products",
            422,
            "IDEMPOTENCY_KEY_REUSED",
            () -> {
              var clave = clave();
              crear(clave, valido());
              return crear(clave, valido());
            }));

    casos.add(
        caso(
            "GET /products",
            400,
            "VALIDATION_FAILED",
            () -> mvc.get().uri("/products?size=0").exchange()));
    var desconocido = UUID.randomUUID();
    for (var ruta :
        new String[][] {
          {"GET /products/{productId}", "/products/%s"},
          {"GET /products/{productId}/stock", "/products/%s/stock"},
          {"GET /products/{productId}/stock/movements", "/products/%s/stock/movements"}
        }) {
      casos.add(
          caso(
              ruta[0],
              400,
              "VALIDATION_FAILED",
              () -> mvc.get().uri(ruta[1].formatted("no-es-un-uuid")).exchange()));
      casos.add(
          caso(
              ruta[0],
              404,
              "PRODUCT_NOT_FOUND",
              () -> mvc.get().uri(ruta[1].formatted(desconocido)).exchange()));
    }

    var op = "PUT /products/{productId}/stock";
    casos.add(caso(op, 400, "VALIDATION_FAILED", () -> fijar(producto(5, 0), clave(), -1)));
    casos.add(caso(op, 400, "IDEMPOTENCY_KEY_MISSING", () -> fijar(producto(5, 0), null, 3)));
    casos.add(
        caso(
            op,
            400,
            "IDEMPOTENCY_KEY_INVALID",
            () -> fijar(producto(5, 0), "clave con espacios", 3)));
    casos.add(caso(op, 404, "PRODUCT_NOT_FOUND", () -> fijar(desconocido, clave(), 3)));
    casos.add(
        caso(op, 409, "STOCK_LIMIT_EXCEEDED", () -> fijar(producto(1, 5), clave(), 10_000_000)));
    casos.add(
        caso(
            op,
            409,
            "IDEMPOTENCY_KEY_IN_PROGRESS",
            () -> {
              var id = producto(5, 0);
              return conClaveRetenida(clave -> fijar(id, clave, 3));
            }));
    casos.add(
        caso(
            op,
            422,
            "IDEMPOTENCY_KEY_REUSED",
            () -> {
              var id = producto(5, 0);
              var clave = clave();
              fijar(id, clave, 3);
              return fijar(id, clave, 4);
            }));
    return casos;
  }

  private static Caso caso(
      String operacion, int estado, String codigo, Supplier<MvcTestResult> ejecutar) {
    return new Caso(operacion, estado, codigo, ejecutar);
  }

  private MvcTestResult conClaveRetenida(
      java.util.function.Function<String, MvcTestResult> solicitud) {
    var clave = clave();
    var bloqueo = redisson.getLock("idem:ms-inventario:" + clave);
    try {
      bloqueo.lockAsync(30, TimeUnit.SECONDS, 4242L).get(5, TimeUnit.SECONDS);
      return solicitud.apply(clave);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      bloqueo.forceUnlock();
    }
  }

  private MvcTestResult crear(String clave, String cuerpo) {
    var solicitud =
        mvc.post().uri("/products").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    return (clave == null ? solicitud : solicitud.header("Idempotency-Key", clave)).exchange();
  }

  private MvcTestResult fijar(UUID id, String clave, int disponible) {
    var solicitud =
        mvc.put()
            .uri("/products/{id}/stock", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"available\":" + disponible + "}");
    return (clave == null ? solicitud : solicitud.header("Idempotency-Key", clave)).exchange();
  }

  private static String valido() {
    return "{\"name\":\"" + NombresUnicos.de("error") + "\",\"initialQuantity\":1}";
  }

  private static String clave() {
    return UUID.randomUUID().toString();
  }

  private UUID producto(int disponible, int reservado) {
    var id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into products (id, name, total, available, reserved, created_at)
            values (:id, :name, :total, :available, :reserved, now())
            """)
        .param("id", id)
        .param("name", NombresUnicos.de("codigos"))
        .param("total", disponible + reservado)
        .param("available", disponible)
        .param("reserved", reservado)
        .update();
    return id;
  }
}
