package com.roshka.challenge.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.pedidos.aplicacion.Respuestas;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ColocarPedido;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "management.opentelemetry.logging.export.schedule-delay=100ms")
@DirtiesContext
@AutoConfigureTracing
@Import(LogsOtlpIT.ExportadorEnMemoria.class)
@ImportTestcontainers(ContenedoresCompartidos.class)
class LogsOtlpIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired ColocarPedido colocarPedido;
  @Autowired RegistroEnMemoria registro;
  @Autowired JsonMapper json;

  @Test
  @DisplayName(
      "BON-03 · 007-AC-01 un log llega al exportador OTLP con el mensaje, el contexto y la traza")
  void debe_exportar_el_log_por_otlp_con_atributos_de_correlacion_cuando_se_maneja_una_respuesta() {
    var pedido = colocarPedido.colocar(List.of(new LineaPedido(UUID.randomUUID(), 1)), "corr-otlp");
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
                assertThat(registro.registros())
                    .filteredOn(
                        r -> atributo(r, "messageId").equals(respuesta.idMensaje().toString()))
                    .anySatisfy(
                        r -> {
                          assertThat(r.getBodyValue().asString()).startsWith("Evento finalizado:");
                          assertThat(atributo(r, "orderId")).isEqualTo(pedido.id().toString());
                          assertThat(atributo(r, "correlationId"))
                              .isEqualTo(respuesta.idCorrelacion());
                          assertThat(r.getSpanContext().isValid()).isTrue();
                        }));
  }

  private static String atributo(LogRecordData registro, String clave) {
    var valor = registro.getAttributes().get(AttributeKey.stringKey(clave));
    return valor == null ? "" : valor;
  }

  static class RegistroEnMemoria implements LogRecordExporter {

    private final List<LogRecordData> registros = new CopyOnWriteArrayList<>();

    List<LogRecordData> registros() {
      return registros;
    }

    @Override
    public CompletableResultCode export(Collection<LogRecordData> logs) {
      registros.addAll(logs);
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      return CompletableResultCode.ofSuccess();
    }
  }

  @TestConfiguration
  static class ExportadorEnMemoria {

    @Bean
    RegistroEnMemoria registroEnMemoria() {
      return new RegistroEnMemoria();
    }
  }
}
