package com.roshka.challenge.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.pedidos.aplicacion.Respuestas;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ColocarPedido;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@AutoConfigureTracing
@AutoConfigureMetrics
@ExtendWith(OutputCaptureExtension.class)
@ImportTestcontainers(ContenedoresCompartidos.class)
class ObservabilidadIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired MockMvcTester mvc;
  @Autowired ColocarPedido colocarPedido;
  @Autowired Tracer tracer;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;
  @Autowired MeterRegistry medidores;

  @Test
  @DisplayName("BON-03 · 006-AC-02 el outbox conserva la traza de la solicitud que lo escribió")
  void debe_publicar_el_comando_en_la_traza_de_la_solicitud_que_coloco_el_pedido() {
    try (var comandos =
        new SondaKafka(
            ContenedoresCompartidos.KAFKA.getBootstrapServers(), Topicos.INVENTARIO_COMANDOS)) {
      var span = tracer.nextSpan().name("test-request").start();
      UUID idPedido;
      try (var alcance = tracer.withSpan(span)) {
        idPedido =
            colocarPedido
                .colocar(List.of(new LineaPedido(UUID.randomUUID(), 1)), "corr-trace")
                .id();
      } finally {
        span.end();
      }
      var idTraza = span.context().traceId();
      var contextoAlmacenado =
          jdbc.sql("select headers ->> 'traceparent' from outbox where aggregate_id = :id")
              .param("id", idPedido)
              .query(String.class)
              .single();
      assertThat(contextoAlmacenado)
          .as("contexto de traza guardado con la fila del outbox")
          .contains(idTraza);

      var registro =
          await()
              .atMost(TIMEOUT)
              .until(() -> comandos.recibidoPara(idPedido.toString()), r -> !r.isEmpty())
              .getFirst();

      var traceparent =
          new String(registro.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8);
      assertThat(traceparent).contains(idTraza);
    }
  }

  @Test
  @DisplayName("BON-03 REQ-18 · 006-AC-05 se exponen métricas de negocio y técnicas")
  void debe_contar_cada_desenlace_exactamente_una_vez_cuando_fluyen_los_pedidos() throws Exception {
    var colocadosAntes = contar("orders.placed");
    var confirmadosAntes =
        contar("orders.by.final.status", "status", "CONFIRMED", "reason", "NONE");
    var duplicadosAntes = contar("messages.duplicated", "type", "StockReservado");
    var mensajesMuertosAntes = contar("messages.dlt", "topic", Topicos.INVENTARIO_EVENTOS);
    var colocado =
        mvc.post()
            .uri("/orders")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}".formatted(UUID.randomUUID()))
            .exchange();
    var ubicacion = colocado.getResponse().getHeader("Location");
    var id = UUID.fromString(ubicacion.substring(ubicacion.lastIndexOf('/') + 1));
    try (var broker =
        new SondaKafka(ContenedoresCompartidos.KAFKA.getBootstrapServers(), "unused")) {
      var respuesta = json.writeValueAsString(Respuestas.stockReservado(id));
      broker.enviar(Topicos.INVENTARIO_EVENTOS, id.toString(), respuesta);
      broker.enviar(Topicos.INVENTARIO_EVENTOS, id.toString(), respuesta);
      broker.enviar(Topicos.INVENTARIO_EVENTOS, UUID.randomUUID().toString(), "not an envelope");
    }

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(contar("orders.placed")).isEqualTo(colocadosAntes + 1);
              assertThat(contar("orders.by.final.status", "status", "CONFIRMED", "reason", "NONE"))
                  .isEqualTo(confirmadosAntes + 1);
              assertThat(contar("messages.duplicated", "type", "StockReservado"))
                  .isEqualTo(duplicadosAntes + 1);
              assertThat(contar("messages.dlt", "topic", Topicos.INVENTARIO_EVENTOS))
                  .isEqualTo(mensajesMuertosAntes + 1);
            });
    var expuesto = mvc.get().uri("/actuator/prometheus").exchange();
    assertThat(expuesto).hasStatus(HttpStatus.OK);
    assertThat(expuesto.getResponse().getContentAsString())
        .contains(
            "orders_placed_total",
            "orders_by_final_status_total",
            "saga_duration_seconds_bucket",
            "messages_duplicated_total",
            "messages_dlt_total",
            "outbox_pending_messages",
            "outbox_publish_lag_seconds_count",
            "distributed_lock_wait_seconds_count");
  }

  private double contar(String nombre, String... etiquetas) {
    var contador = medidores.find(nombre).tags(etiquetas).counter();
    return contador == null ? 0 : contador.count();
  }

  @Test
  @DisplayName("REQ-02 · 006-AC-06 readiness verifica base de datos, Kafka y Redis; liveness no")
  void debe_informar_dependencias_solo_en_readiness() throws Exception {
    var readiness = mvc.get().uri("/actuator/health/readiness").exchange();
    var liveness = mvc.get().uri("/actuator/health/liveness").exchange();

    assertThat(readiness).hasStatus(HttpStatus.OK);
    assertThat(readiness).bodyJson().extractingPath("$.components.db.status").isEqualTo("UP");
    assertThat(readiness).bodyJson().extractingPath("$.components.kafka.status").isEqualTo("UP");
    assertThat(readiness).bodyJson().extractingPath("$.components.redis.status").isEqualTo("UP");
    assertThat(liveness).hasStatus(HttpStatus.OK);
    assertThat(liveness.getResponse().getContentAsString()).doesNotContain("kafka", "redis");
  }

  @Test
  @DisplayName(
      "BON-03 · 006-AC-03 el manejo de un mensaje registra JSON con los ids de traza, pedido y mensaje")
  void debe_registrar_json_con_campos_de_correlacion_cuando_se_maneja_una_respuesta(
      CapturedOutput salida) {
    var pedido = colocarPedido.colocar(List.of(new LineaPedido(UUID.randomUUID(), 1)), "corr-log");
    var respuesta = Respuestas.stockReservado(pedido.id());
    try (var broker =
        new SondaKafka(ContenedoresCompartidos.KAFKA.getBootstrapServers(), "unused")) {
      broker.enviar(
          Topicos.INVENTARIO_EVENTOS, pedido.id().toString(), json.writeValueAsString(respuesta));
    }

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(salida.getOut().lines())
                    .filteredOn(linea -> linea.contains(respuesta.idMensaje().toString()))
                    .anySatisfy(
                        linea -> {
                          var log = json.readTree(linea);
                          assertThat(log.path("messageId").asString())
                              .isEqualTo(respuesta.idMensaje().toString());
                          assertThat(log.path("orderId").asString())
                              .isEqualTo(pedido.id().toString());
                          assertThat(log.path("correlationId").asString())
                              .isEqualTo(respuesta.idCorrelacion());
                          assertThat(log.path("traceId").asString()).isNotBlank();
                          assertThat(log.path("spanId").asString()).isNotBlank();
                        }));
  }
}
