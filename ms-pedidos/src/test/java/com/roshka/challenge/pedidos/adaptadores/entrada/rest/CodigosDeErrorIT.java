package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.pedidos.ContenedoresCompartidos;
import com.roshka.challenge.pedidos.aplicacion.BloqueosPedido;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
      var respuesta = caso.ejecutar().get().getResponse();
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

  private List<Caso> casos() {
    var casos = new ArrayList<Caso>();
    var producto = UUID.randomUUID();
    var sinLineas = "{\"lines\":[]}";
    var repetido =
        "{\"lines\":[{\"productId\":\"%s\",\"quantity\":1},{\"productId\":\"%s\",\"quantity\":2}]}"
            .formatted(producto, producto);
    casos.add(caso("POST /orders", 400, "VALIDATION_FAILED", () -> colocar(clave(), sinLineas)));
    casos.add(
        caso("POST /orders", 400, "DUPLICATE_PRODUCT_LINE", () -> colocar(clave(), repetido)));
    casos.add(caso("POST /orders", 400, "IDEMPOTENCY_KEY_MISSING", () -> colocar(null, valido())));
    casos.add(
        caso(
            "POST /orders",
            400,
            "IDEMPOTENCY_KEY_INVALID",
            () -> colocar("clave con espacios", valido())));
    casos.add(
        caso(
            "POST /orders",
            409,
            "IDEMPOTENCY_KEY_IN_PROGRESS",
            () -> conBloqueo(c -> "idem:ms-pedidos:" + c, clave -> colocar(clave, valido()))));
    casos.add(
        caso(
            "POST /orders",
            422,
            "IDEMPOTENCY_KEY_REUSED",
            () -> {
              var clave = clave();
              colocar(clave, valido());
              return colocar(clave, valido());
            }));

    casos.add(
        caso(
            "GET /orders",
            400,
            "VALIDATION_FAILED",
            () -> mvc.get().uri("/orders?from=2026-10-01").exchange()));
    casos.add(
        caso(
            "GET /orders/{orderId}",
            400,
            "VALIDATION_FAILED",
            () -> mvc.get().uri("/orders/no-es-un-uuid").exchange()));
    casos.add(
        caso(
            "GET /orders/{orderId}",
            404,
            "ORDER_NOT_FOUND",
            () -> mvc.get().uri("/orders/{id}", UUID.randomUUID()).exchange()));

    var op = "DELETE /orders/{orderId}";
    casos.add(caso(op, 400, "VALIDATION_FAILED", () -> cancelar("no-es-un-uuid", clave())));
    casos.add(caso(op, 400, "IDEMPOTENCY_KEY_MISSING", () -> cancelar(pedido().toString(), null)));
    casos.add(
        caso(
            op,
            400,
            "IDEMPOTENCY_KEY_INVALID",
            () -> cancelar(pedido().toString(), "clave con espacios")));
    casos.add(
        caso(op, 404, "ORDER_NOT_FOUND", () -> cancelar(UUID.randomUUID().toString(), clave())));
    casos.add(
        caso(
            op,
            409,
            "ORDER_NOT_CANCELLABLE",
            () -> {
              var id = pedido();
              jdbc.sql(
                      "update orders set status = 'REJECTED', rejection_reason = 'TIMEOUT' where id = :id")
                  .param("id", id)
                  .update();
              return cancelar(id.toString(), clave());
            }));
    casos.add(
        caso(
            op,
            409,
            "CONCURRENT_MODIFICATION",
            () -> {
              var id = pedido();
              return retener(BloqueosPedido.nombrePara(id), () -> cancelar(id.toString(), clave()));
            }));
    casos.add(
        caso(
            op,
            409,
            "IDEMPOTENCY_KEY_IN_PROGRESS",
            () -> {
              var id = pedido();
              return conBloqueo(
                  c -> "idem:ms-pedidos:" + c, clave -> cancelar(id.toString(), clave));
            }));
    casos.add(
        caso(
            op,
            422,
            "IDEMPOTENCY_KEY_REUSED",
            () -> {
              var clave = clave();
              cancelar(pedido().toString(), clave);
              return cancelar(pedido().toString(), clave);
            }));
    return casos;
  }

  private static Caso caso(
      String operacion, int estado, String codigo, Supplier<MvcTestResult> ejecutar) {
    return new Caso(operacion, estado, codigo, ejecutar);
  }

  private MvcTestResult conBloqueo(
      Function<String, String> nombre, Function<String, MvcTestResult> solicitud) {
    var clave = clave();
    return retener(nombre.apply(clave), () -> solicitud.apply(clave));
  }

  private MvcTestResult retener(String nombreBloqueo, Supplier<MvcTestResult> solicitud) {
    var bloqueo = redisson.getLock(nombreBloqueo);
    try {
      bloqueo.lockAsync(30, TimeUnit.SECONDS, 4242L).get(5, TimeUnit.SECONDS);
      return solicitud.get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      bloqueo.forceUnlock();
    }
  }

  private MvcTestResult colocar(String clave, String cuerpo) {
    var solicitud =
        mvc.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    return (clave == null ? solicitud : solicitud.header("Idempotency-Key", clave)).exchange();
  }

  private MvcTestResult cancelar(String id, String clave) {
    var solicitud = mvc.delete().uri("/orders/{id}", id);
    return (clave == null ? solicitud : solicitud.header("Idempotency-Key", clave)).exchange();
  }

  private UUID pedido() {
    var respuesta = colocar(clave(), valido()).getResponse();
    try {
      return UUID.fromString(
          json.readTree(respuesta.getContentAsString(StandardCharsets.UTF_8))
              .get("orderId")
              .asString());
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String valido() {
    return "{\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}".formatted(UUID.randomUUID());
  }

  private static String clave() {
    return UUID.randomUUID().toString();
  }
}
