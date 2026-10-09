package com.roshka.challenge.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.contrato.LineaStock;
import com.roshka.challenge.contrato.ReservarStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.RegistrarProducto;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
  private static final String ID_TRAZA = "4bf92f3577b34da6a3ce929d0e0e4736";

  @Autowired MockMvcTester mvc;
  @Autowired RegistrarProducto registrarProducto;
  @Autowired JsonMapper json;
  @Autowired MeterRegistry medidores;

  @Test
  @DisplayName("BON-03 · 006-AC-01/02 un comando y su respuesta quedan en la misma traza")
  void debe_responder_en_la_traza_del_comando() {
    var idPedido = UUID.randomUUID();
    var producto = registrarProducto.registrar(NombresUnicos.de("traced"), 5).id();
    try (var eventos =
        new SondaKafka(
            ContenedoresCompartidos.KAFKA.getBootstrapServers(), Topicos.INVENTARIO_EVENTOS)) {
      eventos.enviar(
          Topicos.INVENTARIO_COMANDOS,
          idPedido.toString(),
          json.writeValueAsString(reservar(idPedido, producto)),
          Map.of("traceparent", "00-" + ID_TRAZA + "-00f067aa0ba902b7-01"));

      var respuesta =
          await()
              .atMost(TIMEOUT)
              .until(() -> eventos.recibidoPara(idPedido.toString()), r -> !r.isEmpty())
              .getFirst();

      var traceparent =
          new String(respuesta.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8);
      assertThat(traceparent).contains(ID_TRAZA);
    }
  }

  @Test
  @DisplayName("BON-03 REQ-18 · 006-AC-05 se exponen métricas de negocio y técnicas")
  void debe_contar_cada_desenlace_exactamente_una_vez_cuando_fluyen_los_comandos()
      throws Exception {
    var reservadoAntes = contar("stock.reservations", "result", "reserved", "reason", "NONE");
    var rechazadosAntes =
        contar("stock.reservations", "result", "rejected", "reason", "INSUFFICIENT_STOCK");
    var duplicadosAntes = contar("messages.duplicated", "type", "ReservarStock");
    var mensajesMuertosAntes = contar("messages.dlt", "topic", Topicos.INVENTARIO_COMANDOS);
    var producto = registrarProducto.registrar(NombresUnicos.de("metered"), 1).id();
    var solicitudBloqueada =
        mvc.post()
            .uri("/products")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"name\":\"" + NombresUnicos.de("metered-http") + "\",\"initialQuantity\":1}")
            .exchange();
    assertThat(solicitudBloqueada).hasStatus(HttpStatus.CREATED);
    var cabe = reservar(UUID.randomUUID(), producto);
    var excede = reservar(UUID.randomUUID(), producto);
    try (var broker =
        new SondaKafka(ContenedoresCompartidos.KAFKA.getBootstrapServers(), "unused")) {
      enviar(broker, cabe);
      enviar(broker, cabe);
      enviar(broker, excede);
      broker.enviar(Topicos.INVENTARIO_COMANDOS, UUID.randomUUID().toString(), "not an envelope");
    }

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(contar("stock.reservations", "result", "reserved", "reason", "NONE"))
                  .isEqualTo(reservadoAntes + 1);
              assertThat(
                      contar(
                          "stock.reservations",
                          "result",
                          "rejected",
                          "reason",
                          "INSUFFICIENT_STOCK"))
                  .isEqualTo(rechazadosAntes + 1);
              assertThat(contar("messages.duplicated", "type", "ReservarStock"))
                  .isEqualTo(duplicadosAntes + 1);
              assertThat(contar("messages.dlt", "topic", Topicos.INVENTARIO_COMANDOS))
                  .isEqualTo(mensajesMuertosAntes + 1);
            });
    var expuesto = mvc.get().uri("/actuator/prometheus").exchange();
    assertThat(expuesto).hasStatus(HttpStatus.OK);
    assertThat(expuesto.getResponse().getContentAsString())
        .contains(
            "stock_reservations_total",
            "messages_duplicated_total",
            "messages_dlt_total",
            "outbox_pending_messages",
            "outbox_publish_lag_seconds_count",
            "distributed_lock_wait_seconds_count");
  }

  private void enviar(SondaKafka broker, SobreMensaje<ReservarStock> comando) {
    broker.enviar(
        Topicos.INVENTARIO_COMANDOS,
        comando.idAgregado().toString(),
        json.writeValueAsString(comando));
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
    assertThat(liveness.getResponse().getContentAsString()).doesNotContain("kafka", "redis");
  }

  @Test
  @DisplayName(
      "BON-03 · 006-AC-03 el manejo de un comando registra JSON con los ids de traza, pedido y mensaje")
  void debe_registrar_json_con_campos_de_correlacion_cuando_se_maneja_un_comando(
      CapturedOutput salida) {
    var idPedido = UUID.randomUUID();
    var comando =
        reservar(idPedido, registrarProducto.registrar(NombresUnicos.de("logged"), 5).id());
    try (var broker =
        new SondaKafka(ContenedoresCompartidos.KAFKA.getBootstrapServers(), "unused")) {
      broker.enviar(
          Topicos.INVENTARIO_COMANDOS, idPedido.toString(), json.writeValueAsString(comando));
    }

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(salida.getOut().lines())
                    .filteredOn(linea -> linea.contains(comando.idMensaje().toString()))
                    .anySatisfy(
                        linea -> {
                          var log = json.readTree(linea);
                          assertThat(log.path("orderId").asString()).isEqualTo(idPedido.toString());
                          assertThat(log.path("correlationId").asString())
                              .isEqualTo(comando.idCorrelacion());
                          assertThat(log.path("messageId").asString())
                              .isEqualTo(comando.idMensaje().toString());
                          assertThat(log.path("traceId").asString()).isNotBlank();
                          assertThat(log.path("spanId").asString()).isNotBlank();
                        }));
  }

  private static SobreMensaje<ReservarStock> reservar(UUID idPedido, UUID idProducto) {
    return new SobreMensaje<>(
        UUID.randomUUID(),
        TiposMensaje.RESERVAR_STOCK,
        TiposMensaje.VERSION_ESQUEMA,
        Instant.now(),
        "corr-" + idPedido,
        null,
        idPedido,
        new ReservarStock(idPedido, List.of(new LineaStock(idProducto, 1))));
  }
}
