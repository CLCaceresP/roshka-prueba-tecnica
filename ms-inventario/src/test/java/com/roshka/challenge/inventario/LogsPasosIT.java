package com.roshka.challenge.inventario;

import static com.roshka.challenge.inventario.ExportadorLogsEnMemoria.atributo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "management.opentelemetry.logging.export.schedule-delay=100ms")
@DirtiesContext
@AutoConfigureMockMvc
@AutoConfigureTracing
@Import(ExportadorLogsEnMemoria.Configuracion.class)
@ImportTestcontainers(ContenedoresCompartidos.class)
class LogsPasosIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired MockMvcTester mvc;
  @Autowired ExportadorLogsEnMemoria exportador;
  @Autowired JsonMapper json;

  @Test
  @DisplayName("BON-03 · 010-AC-03 registrar un producto se lee paso a paso con sus tiempos")
  void debe_registrar_el_recorrido_de_registrar_un_producto_en_orden_con_sus_duraciones() {
    var idCorrelacion = "corr-" + UUID.randomUUID();
    var nombre = NombresUnicos.de("paso-a-paso");

    var respuesta = registrar(idCorrelacion, UUID.randomUUID(), nombre, 4);

    var idProducto = json.readTree(respuesta.cuerpo()).get("productId").asString();
    assertThat(respuesta.location()).isEqualTo("/products/" + idProducto);
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var registros = recorrido(idCorrelacion);
              var mensajes = registros.stream().map(r -> r.getBodyValue().asString()).toList();
              var recibida = indice(mensajes, "Solicitud HTTP recibida: POST /products");
              var clave = indice(mensajes, "Clave de idempotencia consultada");
              var creado = indice(mensajes, "Producto creado y validado");
              var verificado = indice(mensajes, "Nombre verificado (sin duplicados)");
              var guardado = indice(mensajes, "Producto guardado en la base de datos");
              var registrado = indice(mensajes, "Producto " + idProducto + " registrado");
              var proceso = indice(mensajes, "Registrar producto completado en");
              var respuestaRegistrada =
                  indice(mensajes, "Solicitud ejecutada y respuesta registrada");
              var procesada = indice(mensajes, "Solicitud HTTP procesada: POST /products");
              assertThat(recibida).isGreaterThanOrEqualTo(0);
              assertThat(clave).isGreaterThan(recibida);
              assertThat(creado).isGreaterThan(clave);
              assertThat(verificado).isGreaterThan(creado);
              assertThat(guardado).isGreaterThan(verificado);
              assertThat(registrado).isGreaterThan(guardado);
              assertThat(proceso).isGreaterThan(registrado);
              assertThat(respuestaRegistrada).isGreaterThan(proceso);
              assertThat(procesada).isGreaterThan(respuestaRegistrada);
              assertThat(mensajes.get(procesada))
                  .containsPattern("respondió 201 en \\d+ ms")
                  .contains("Location: /products/" + idProducto);
              assertThat(atributo(registros.get(guardado), "durationMs")).matches("\\d+");
              assertThat(atributo(registros.get(guardado), "productId")).isEqualTo(idProducto);
            });
  }

  @Test
  @DisplayName(
      "BON-03 · 010-AC-04 repetir la solicitud reproduce la respuesta sin volver a guardar")
  void debe_registrar_la_solicitud_repetida_sin_guardar_de_nuevo_cuando_se_repite_la_clave() {
    var clave = UUID.randomUUID();
    var nombre = NombresUnicos.de("repetido");
    registrar("corr-original-" + clave, clave, nombre, 1);
    var repetida = "corr-repetida-" + clave;

    registrar(repetida, clave, nombre, 1);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var mensajes =
                  recorrido(repetida).stream().map(r -> r.getBodyValue().asString()).toList();
              assertThat(mensajes)
                  .anyMatch(
                      m ->
                          m.startsWith("Solicitud repetida: se reprodujo la respuesta almacenada"));
              assertThat(mensajes).noneMatch(m -> m.startsWith("Producto guardado"));
            });
  }

  @Test
  @DisplayName("BON-03 · 010-AC-12 un nombre repetido dice cuál producto ya lo tiene")
  void debe_registrar_el_conflicto_con_el_producto_existente_cuando_el_nombre_ya_existe() {
    var nombre = NombresUnicos.de("conflicto");
    var existente =
        json.readTree(registrar("corr-" + UUID.randomUUID(), UUID.randomUUID(), nombre, 1).cuerpo())
            .get("productId")
            .asString();
    var idCorrelacion = "corr-conflicto-" + UUID.randomUUID();

    registrar(idCorrelacion, UUID.randomUUID(), nombre.toUpperCase(), 2);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        recorrido(idCorrelacion).stream()
                            .filter(
                                r ->
                                    r.getBodyValue()
                                        .asString()
                                        .startsWith(
                                            "Respuesta de error 409 PRODUCT_ALREADY_EXISTS"))
                            .toList())
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getBodyValue().asString()).contains(existente);
                          assertThat(r.getSeverity()).isEqualTo(Severity.WARN);
                          assertThat(atributo(r, "status")).isEqualTo("409");
                          assertThat(atributo(r, "code")).isEqualTo("PRODUCT_ALREADY_EXISTS");
                        }));
  }

  @Test
  @DisplayName("BON-03 · 010-AC-12 un producto inexistente dice cuál se buscó")
  void debe_registrar_el_error_con_su_codigo_cuando_el_producto_no_existe() {
    var inexistente = UUID.randomUUID();
    var idCorrelacion = "corr-error-" + UUID.randomUUID();

    mvc.get()
        .uri("/products/{id}/stock", inexistente)
        .header("X-Correlation-Id", idCorrelacion)
        .exchange();

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        recorrido(idCorrelacion).stream()
                            .filter(
                                r ->
                                    r.getBodyValue()
                                        .asString()
                                        .startsWith("Respuesta de error 404 PRODUCT_NOT_FOUND"))
                            .toList())
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getBodyValue().asString()).contains(inexistente.toString());
                          assertThat(r.getSeverity()).isEqualTo(Severity.WARN);
                          assertThat(atributo(r, "code")).isEqualTo("PRODUCT_NOT_FOUND");
                        }));
  }

  @Test
  @DisplayName("BON-03 · 010-AC-12 una validación fallida dice cuál fue")
  void debe_registrar_el_detalle_de_la_validacion_cuando_la_cantidad_es_negativa() {
    var idCorrelacion = "corr-validacion-" + UUID.randomUUID();

    registrar(idCorrelacion, UUID.randomUUID(), NombresUnicos.de("invalido"), -1);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        recorrido(idCorrelacion).stream()
                            .map(r -> r.getBodyValue().asString())
                            .toList())
                    .anyMatch(m -> m.startsWith("Respuesta de error 400 VALIDATION_FAILED: ")));
  }

  @Test
  @DisplayName("BON-03 · 010-AC-05 el bloqueo de la clave dice cuánto esperó y cuánto retuvo")
  void debe_registrar_el_bloqueo_adquirido_y_liberado_con_sus_tiempos_cuando_se_registra() {
    var idCorrelacion = "corr-bloqueo-" + UUID.randomUUID();
    var clave = UUID.randomUUID();

    registrar(idCorrelacion, clave, NombresUnicos.de("bloqueo"), 1);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var mensajes =
                  recorrido(idCorrelacion).stream().map(r -> r.getBodyValue().asString()).toList();
              var adquirido =
                  indice(mensajes, "Bloqueo idem:ms-inventario:" + clave + " adquirido en");
              var guardado = indice(mensajes, "Producto guardado en la base de datos");
              var liberado =
                  indice(
                      mensajes, "Bloqueo idem:ms-inventario:" + clave + " liberado tras retenerlo");
              assertThat(adquirido).isGreaterThanOrEqualTo(0);
              assertThat(guardado).isGreaterThan(adquirido);
              assertThat(liberado).isGreaterThan(guardado);
            });
  }

  @Test
  @DisplayName("BON-03 · 012-AC-07 el listado de productos dice cuántos devolvió y de cuántos")
  void debe_registrar_cuantos_productos_devolvio_y_de_cuantos_cuando_se_lista() throws Exception {
    var idCorrelacion = "corr-listado-" + UUID.randomUUID();
    registrar("corr-alta-" + UUID.randomUUID(), UUID.randomUUID(), NombresUnicos.de("listado"), 1);

    var respuesta =
        mvc.get()
            .uri("/products?page=0&size=1")
            .header("X-Correlation-Id", idCorrelacion)
            .exchange();

    var total =
        json.readTree(respuesta.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("totalElements")
            .asLong();
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        recorrido(idCorrelacion).stream()
                            .filter(
                                r ->
                                    r.getBodyValue()
                                        .asString()
                                        .startsWith("Listado de productos: página 0 de"))
                            .toList())
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getBodyValue().asString())
                              .endsWith("(1 de %d productos)".formatted(total));
                          assertThat(atributo(r, "returned")).isEqualTo("1");
                          assertThat(atributo(r, "totalElements")).isEqualTo(String.valueOf(total));
                        }));
  }

  @Test
  @DisplayName("BON-03 · 012-AC-07 consultar un producto dice cuál devolvió y con cuánto stock")
  void debe_registrar_el_producto_consultado_con_su_stock_cuando_se_pide_por_id() {
    var idCorrelacion = "corr-producto-" + UUID.randomUUID();
    var alta =
        registrar(
            "corr-alta-" + UUID.randomUUID(), UUID.randomUUID(), NombresUnicos.de("consultado"), 7);
    var idProducto = json.readTree(alta.cuerpo()).get("productId").asString();

    mvc.get()
        .uri("/products/{id}", idProducto)
        .header("X-Correlation-Id", idCorrelacion)
        .exchange();

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(recorrido(idCorrelacion).stream().map(r -> r.getBodyValue().asString()))
                    .contains(
                        "Producto %s: 7 disponibles, 0 reservados, 7 en total"
                            .formatted(idProducto)));
  }

  @Test
  @DisplayName(
      "BON-03 · 014-AC-11 el cambio de stock cuenta cuánto cambió y un rechazo dice por qué")
  void debe_registrar_cuanto_cambio_el_stock_y_por_que_se_rechaza_un_valor() {
    var idCorrelacion = "corr-ajuste-" + UUID.randomUUID();
    var alta =
        registrar(
            "corr-alta-" + UUID.randomUUID(), UUID.randomUUID(), NombresUnicos.de("ajustable"), 5);
    var idProducto = json.readTree(alta.cuerpo()).get("productId").asString();

    fijarDisponible(idCorrelacion, idProducto, 15);
    fijarDisponible(idCorrelacion, idProducto, 10_000_001);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var mensajes =
                  recorrido(idCorrelacion).stream().map(r -> r.getBodyValue().asString()).toList();
              assertThat(mensajes)
                  .contains(
                      "Stock del producto %s fijado en 15 disponibles (antes 5): se agregaron 10 unidades"
                          .formatted(idProducto));
              assertThat(mensajes)
                  .anyMatch(m -> m.startsWith("Stock fijado en la base de datos ("));
              assertThat(mensajes).anyMatch(m -> m.startsWith("Movimiento registrado ("));
              assertThat(mensajes)
                  .anyMatch(
                      m ->
                          m.startsWith("Respuesta de error 400 VALIDATION_FAILED: ")
                              && m.contains("available")
                              && m.contains("entre 0 y 10000000"));
            });
  }

  private void fijarDisponible(String idCorrelacion, String idProducto, int disponible) {
    mvc.put()
        .uri("/products/{id}/stock", idProducto)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .header("X-Correlation-Id", idCorrelacion)
        .content("{\"available\":%d}".formatted(disponible))
        .exchange();
  }

  private Respuesta registrar(String idCorrelacion, UUID clave, String nombre, int cantidad) {
    var resultado =
        mvc.post()
            .uri("/products")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", clave.toString())
            .header("X-Correlation-Id", idCorrelacion)
            .content("{\"name\":\"%s\",\"initialQuantity\":%d}".formatted(nombre, cantidad))
            .exchange();
    try {
      var http = resultado.getResponse();
      return new Respuesta(http.getContentAsString(), http.getHeader("Location"));
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private List<LogRecordData> recorrido(String idCorrelacion) {
    return exportador.registros().stream()
        .filter(r -> idCorrelacion.equals(atributo(r, "correlationId")))
        .sorted(Comparator.comparingLong(LogRecordData::getObservedTimestampEpochNanos))
        .toList();
  }

  private static int indice(List<String> mensajes, String prefijo) {
    for (int i = 0; i < mensajes.size(); i++) {
      if (mensajes.get(i).startsWith(prefijo)) {
        return i;
      }
    }
    return -1;
  }

  private record Respuesta(String cuerpo, String location) {}
}
