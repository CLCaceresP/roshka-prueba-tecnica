package com.roshka.challenge.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "management.opentelemetry.logging.export.schedule-delay=100ms")
@AutoConfigureTracing
@Import(LogsOtlpIT.ExportadorEnMemoria.class)
@Testcontainers
class LogsOtlpIT {

  @Container
  @ServiceConnection(name = "redis")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.8-alpine").withExposedPorts(6379);

  @Autowired RegistroEnMemoria registro;

  @Test
  @DisplayName("BON-03 · 007-AC-05 el log de arranque del gateway se exporta por OTLP")
  void debe_exportar_el_log_de_arranque_por_otlp_con_el_nombre_del_servicio_cuando_arranca() {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(registro.registros())
                    .anySatisfy(
                        r -> {
                          assertThat(r.getBodyValue().asString()).contains("Netty started on port");
                          assertThat(r.getSeverityText()).isEqualTo("INFO");
                          assertThat(
                                  r.getResource()
                                      .getAttribute(AttributeKey.stringKey("service.name")))
                              .isEqualTo("gateway-pedidos-inventario");
                        }));
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
