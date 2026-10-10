package com.roshka.challenge.pedidos;

import static com.roshka.challenge.pedidos.ExportadorLogsEnMemoria.atributo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.pedidos.aplicacion.consulta.ConfiguracionConsulta;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
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
  @Autowired ConfiguracionConsulta zona;

  @Test
  @DisplayName("BON-03 · 010-AC-02 colocar un pedido se lee paso a paso con sus tiempos")
  void debe_registrar_el_recorrido_de_colocar_un_pedido_en_orden_con_sus_duraciones() {
    var idCorrelacion = "corr-" + UUID.randomUUID();
    var producto = UUID.randomUUID();

    var respuesta = colocar(idCorrelacion, UUID.randomUUID(), producto, 2, "{}");

    var idPedido = json.readTree(respuesta.cuerpo()).get("orderId").asString();
    assertThat(respuesta.location()).isEqualTo("/orders/" + idPedido);
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var registros = recorrido(idCorrelacion);
              var mensajes = registros.stream().map(r -> r.getBodyValue().asString()).toList();
              var recibida = indice(mensajes, "Solicitud HTTP recibida: POST /orders");
              var clave = indice(mensajes, "Clave de idempotencia consultada");
              var creado = indice(mensajes, "Pedido creado y validado");
              var guardado = indice(mensajes, "Pedido guardado en la base de datos");
              var colocado = indice(mensajes, "Pedido " + idPedido + " colocado");
              var comando = indice(mensajes, "Comando ReservarStock registrado en el outbox");
              var proceso = indice(mensajes, "Colocar pedido completado en");
              var registrada = indice(mensajes, "Solicitud ejecutada y respuesta registrada");
              var procesada = indice(mensajes, "Solicitud HTTP procesada: POST /orders");
              assertThat(recibida).isGreaterThanOrEqualTo(0);
              assertThat(clave).isGreaterThan(recibida);
              assertThat(creado).isGreaterThan(clave);
              assertThat(guardado).isGreaterThan(creado);
              assertThat(colocado).isGreaterThan(guardado);
              assertThat(comando).isGreaterThan(colocado);
              assertThat(proceso).isGreaterThan(comando);
              assertThat(registrada).isGreaterThan(proceso);
              assertThat(procesada).isGreaterThan(registrada);
              assertThat(mensajes.get(recibida)).contains("POST /orders");
              assertThat(mensajes.get(procesada))
                  .containsPattern("respondió 202 en \\d+ ms")
                  .contains("Location: /orders/" + idPedido);
              assertThat(atributo(registros.get(guardado), "durationMs")).matches("\\d+");
              assertThat(atributo(registros.get(guardado), "step"))
                  .isEqualTo("Pedido guardado en la base de datos");
              assertThat(atributo(registros.get(guardado), "orderId")).isEqualTo(idPedido);
            });
  }

  @Test
  @DisplayName(
      "BON-03 · 010-AC-04 repetir la solicitud reproduce la respuesta sin volver a guardar")
  void debe_registrar_la_solicitud_repetida_sin_guardar_de_nuevo_cuando_se_repite_la_clave() {
    var clave = UUID.randomUUID();
    var producto = UUID.randomUUID();
    colocar("corr-original-" + clave, clave, producto, 1, "{}");
    var repetida = "corr-repetida-" + clave;

    colocar(repetida, clave, producto, 1, "{}");

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
              assertThat(mensajes).noneMatch(m -> m.startsWith("Pedido guardado"));
              assertThat(mensajes)
                  .anyMatch(m -> m.startsWith("Solicitud HTTP procesada: POST /orders"));
            });
  }

  @Test
  @DisplayName("BON-03 · 010-AC-02 cancelar un pedido se lee paso a paso con sus tiempos")
  void debe_registrar_el_recorrido_de_cancelar_un_pedido_con_sus_duraciones() {
    var producto = UUID.randomUUID();
    var creado = colocar("corr-" + UUID.randomUUID(), UUID.randomUUID(), producto, 1, "{}");
    var idPedido = json.readTree(creado.cuerpo()).get("orderId").asString();
    var idCorrelacion = "corr-cancelar-" + UUID.randomUUID();

    mvc.delete()
        .uri("/orders/{id}", idPedido)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .header("X-Correlation-Id", idCorrelacion)
        .exchange();

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var mensajes =
                  recorrido(idCorrelacion).stream().map(r -> r.getBodyValue().asString()).toList();
              var cargado = indice(mensajes, "Pedido cargado");
              var aplicada = indice(mensajes, "Cancelación aplicada");
              var actualizado = indice(mensajes, "Pedido actualizado y comandos registrados");
              var proceso = indice(mensajes, "Cancelar pedido completado en");
              assertThat(cargado).isGreaterThanOrEqualTo(0);
              assertThat(aplicada).isGreaterThan(cargado);
              assertThat(actualizado).isGreaterThan(aplicada);
              assertThat(proceso).isGreaterThan(actualizado);
              assertThat(mensajes).anyMatch(m -> m.startsWith("Pedido " + idPedido + " cancelado"));
            });
  }

  @Test
  @DisplayName("BON-03 · 010-AC-07 el id del pedido lo genera el servidor; uno propio es 400")
  void debe_generar_el_id_del_pedido_en_el_servidor_y_rechazar_el_que_envia_el_cliente() {
    var propio = UUID.randomUUID().toString();
    var producto = UUID.randomUUID();

    var conIdPropio =
        colocar(
            "corr-" + UUID.randomUUID(),
            UUID.randomUUID(),
            producto,
            1,
            "\"orderId\":\"" + propio + "\"");
    var primero = colocar("corr-" + UUID.randomUUID(), UUID.randomUUID(), producto, 1, "{}");
    var segundo = colocar("corr-" + UUID.randomUUID(), UUID.randomUUID(), producto, 1, "{}");

    assertThat(conIdPropio.cuerpo()).contains("VALIDATION_FAILED").contains("orderId");
    var idPrimero = json.readTree(primero.cuerpo()).get("orderId").asString();
    var idSegundo = json.readTree(segundo.cuerpo()).get("orderId").asString();
    assertThat(idPrimero).isNotEqualTo(idSegundo);
    assertThat(UUID.fromString(idPrimero)).isNotNull();
  }

  @Test
  @DisplayName("BON-03 · 010-AC-11 consultar un pedido dice qué devolvió")
  void debe_registrar_el_pedido_consultado_con_su_estado_y_sus_lineas() {
    var producto = UUID.randomUUID();
    var creado = colocar("corr-" + UUID.randomUUID(), UUID.randomUUID(), producto, 3, "{}");
    var idPedido = json.readTree(creado.cuerpo()).get("orderId").asString();
    var idCorrelacion = "corr-consulta-" + UUID.randomUUID();

    mvc.get().uri("/orders/{id}", idPedido).header("X-Correlation-Id", idCorrelacion).exchange();

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
                                        .startsWith("Pedido " + idPedido + " consultado"))
                            .toList())
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getBodyValue().asString())
                              .contains("estado PENDING")
                              .contains("1 líneas, 3 unidades")
                              .contains(producto + "×3");
                          assertThat(atributo(r, "orderId")).isEqualTo(idPedido);
                          assertThat(atributo(r, "status")).isEqualTo("PENDING");
                          assertThat(atributo(r, "units")).isEqualTo("3");
                        }));
  }

  @Test
  @DisplayName("BON-03 · 010-AC-12 un error HTTP dice por qué")
  void debe_registrar_el_error_con_su_codigo_y_su_detalle_cuando_el_pedido_no_existe() {
    var inexistente = UUID.randomUUID();
    var idCorrelacion = "corr-error-" + UUID.randomUUID();

    mvc.get().uri("/orders/{id}", inexistente).header("X-Correlation-Id", idCorrelacion).exchange();

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
                                        .startsWith("Respuesta de error 404 ORDER_NOT_FOUND"))
                            .toList())
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getBodyValue().asString())
                              .isEqualTo(
                                  "Respuesta de error 404 ORDER_NOT_FOUND: El pedido "
                                      + inexistente
                                      + " no existe");
                          assertThat(r.getSeverity()).isEqualTo(Severity.WARN);
                          assertThat(atributo(r, "status")).isEqualTo("404");
                          assertThat(atributo(r, "code")).isEqualTo("ORDER_NOT_FOUND");
                        }));
  }

  @Test
  @DisplayName("BON-03 · 010-AC-12 una validación fallida dice cuál fue")
  void debe_registrar_el_detalle_de_la_validacion_cuando_el_pedido_no_trae_lineas() {
    var idCorrelacion = "corr-validacion-" + UUID.randomUUID();

    mvc.post()
        .uri("/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .header("X-Correlation-Id", idCorrelacion)
        .content("{\"lines\":[]}")
        .exchange();

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
  @DisplayName("BON-03 · 013-AC-09 el listado de pedidos dice el rango y cuántos devolvió")
  void debe_registrar_el_rango_y_lo_devuelto_cuando_se_listan_pedidos() throws Exception {
    var idCorrelacion = "corr-listado-" + UUID.randomUUID();
    colocar("corr-alta-" + UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "{}");
    var hoy = LocalDate.now(zona.zona());
    var texto = "%02d/%02d/%d".formatted(hoy.getMonthValue(), hoy.getDayOfMonth(), hoy.getYear());

    var respuesta =
        mvc.get()
            .uri("/orders?from={d}&to={d}&size=5", texto, texto)
            .header("X-Correlation-Id", idCorrelacion)
            .exchange();

    var cuerpo = json.readTree(respuesta.getResponse().getContentAsString(StandardCharsets.UTF_8));
    var devueltos = cuerpo.get("items").size();
    var total = cuerpo.get("totalElements").asLong();
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
                                        .startsWith("Listado de pedidos del " + texto))
                            .toList())
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getBodyValue().asString())
                              .startsWith(
                                  "Listado de pedidos del %s al %s: página 0 de "
                                      .formatted(texto, texto))
                              .contains("(%d de %d pedidos)".formatted(devueltos, total));
                          assertThat(atributo(r, "returned")).isEqualTo(String.valueOf(devueltos));
                          assertThat(atributo(r, "totalElements")).isEqualTo(String.valueOf(total));
                        }));
  }

  private Respuesta colocar(
      String idCorrelacion, UUID clave, UUID producto, int cantidad, String extra) {
    var cuerpo =
        "{%s\"lines\":[{\"productId\":\"%s\",\"quantity\":%d}]}"
            .formatted(extra.equals("{}") ? "" : extra + ",", producto, cantidad);
    var resultado =
        mvc.post()
            .uri("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", clave.toString())
            .header("X-Correlation-Id", idCorrelacion)
            .content(cuerpo)
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
