package com.roshka.challenge.inventario;

import static com.roshka.challenge.inventario.ExportadorLogsEnMemoria.atributo;
import static com.roshka.challenge.inventario.ExportadorLogsEnMemoria.recurso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.inventario.aplicacion.puertos.entrada.RegistrarProducto;
import io.opentelemetry.api.logs.Severity;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "management.opentelemetry.logging.export.schedule-delay=100ms")
@DirtiesContext
@AutoConfigureMockMvc
@AutoConfigureTracing
@Import(ExportadorLogsEnMemoria.Configuracion.class)
@ImportTestcontainers(ContenedoresCompartidos.class)
@ExtendWith(OutputCaptureExtension.class)
class LogsSolicitudesIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final String MENSAJE = "Solicitud HTTP procesada";

  @Autowired MockMvcTester mvc;
  @Autowired RegistrarProducto registrarProducto;
  @Autowired ExportadorLogsEnMemoria exportador;

  @Test
  @DisplayName(
      "BON-03 · 008-AC-01 una línea por solicitud con ruta, estado, duración, cliente y traza")
  void debe_registrar_una_linea_por_solicitud_con_su_contexto_cuando_se_consulta_el_stock() {
    var producto = registrarProducto.registrar(NombresUnicos.de("registrado"), 5);
    var idCorrelacion = "corr-" + UUID.randomUUID();

    mvc.get()
        .uri("/products/{id}/stock", producto.id())
        .header("X-Correlation-Id", idCorrelacion)
        .header("User-Agent", "agente-de-prueba")
        .exchange();

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(exportador.registros())
                    .filteredOn(
                        r ->
                            r.getBodyValue().asString().startsWith(MENSAJE)
                                && idCorrelacion.equals(atributo(r, "correlationId")))
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getSeverity()).isEqualTo(Severity.INFO);
                          assertThat(atributo(r, "method")).isEqualTo("GET");
                          assertThat(atributo(r, "path")).isEqualTo("/products/{productId}/stock");
                          assertThat(atributo(r, "status")).isEqualTo("200");
                          assertThat(atributo(r, "durationMs")).isNotBlank();
                          assertThat(atributo(r, "clientIp")).isNotBlank();
                          assertThat(atributo(r, "userAgent")).isEqualTo("agente-de-prueba");
                          assertThat(r.getSpanContext().isValid()).isTrue();
                        }));
  }

  @Test
  @DisplayName("BON-03 · 008-AC-02 una respuesta 404 se registra en nivel WARN")
  void debe_registrar_en_warn_cuando_la_respuesta_es_404() {
    var idCorrelacion = "corr-" + UUID.randomUUID();

    mvc.get()
        .uri("/products/{id}/stock", UUID.randomUUID())
        .header("X-Correlation-Id", idCorrelacion)
        .exchange();

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(exportador.registros())
                    .filteredOn(
                        r ->
                            r.getBodyValue().asString().startsWith(MENSAJE)
                                && idCorrelacion.equals(atributo(r, "correlationId")))
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getSeverity()).isEqualTo(Severity.WARN);
                          assertThat(atributo(r, "status")).isEqualTo("404");
                        }));
  }

  @Test
  @DisplayName("BON-03 · 008-AC-03 las sondas de salud no se registran en INFO")
  void debe_omitir_las_sondas_de_salud_entre_los_logs_info() {
    var sonda = "corr-sonda-" + UUID.randomUUID();
    var siguiente = "corr-siguiente-" + UUID.randomUUID();

    mvc.get().uri("/actuator/health/readiness").header("X-Correlation-Id", sonda).exchange();
    mvc.get()
        .uri("/products/{id}/stock", UUID.randomUUID())
        .header("X-Correlation-Id", siguiente)
        .exchange();

    await()
        .atMost(TIMEOUT)
        .until(
            () ->
                exportador.registros().stream()
                    .anyMatch(r -> siguiente.equals(atributo(r, "correlationId"))));
    assertThat(exportador.registros())
        .noneMatch(
            r ->
                sonda.equals(atributo(r, "correlationId"))
                    && r.getBodyValue().asString().startsWith("Solicitud HTTP"));
  }

  @Test
  @DisplayName(
      "BON-03 · 008-AC-05 los logs exportados llevan los atributos de recurso de la aplicación")
  void debe_exportar_atributos_de_recurso_y_de_hilo_cuando_registra_un_log() throws Exception {
    mvc.get().uri("/products/{id}/stock", UUID.randomUUID()).exchange();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(exportador.registros())
                    .anySatisfy(
                        r -> {
                          assertThat(recurso(r, "service.name")).isEqualTo("ms-inventario");
                          assertThat(recurso(r, "service.namespace"))
                              .isEqualTo("pedidos-inventario");
                          assertThat(recurso(r, "service.version")).matches("\\d+\\.\\d+\\.\\d+.*");
                          assertThat(recurso(r, "deployment.environment.name")).isEqualTo("local");
                          assertThat(recurso(r, "host.name")).isNotBlank();
                          assertThat(recurso(r, "process.pid")).matches("[1-9]\\d*");
                          assertThat(atributo(r, "thread.name")).isNotBlank();
                        }));
  }

  @Test
  @DisplayName(
      "BON-03 · 008-AC-05 la consola JSON incluye aplicación, grupo, entorno y nombre de máquina")
  void debe_incluir_aplicacion_grupo_y_entorno_en_la_consola_json(CapturedOutput salida)
      throws Exception {
    mvc.get().uri("/products/{id}/stock", UUID.randomUUID()).exchange();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(salida.getOut().lines().filter(l -> l.startsWith("{")))
                    .anySatisfy(
                        linea -> {
                          var log = JsonMapper.builder().build().readTree(linea);
                          assertThat(log.path("application").asString()).isEqualTo("ms-inventario");
                          assertThat(log.path("group").asString()).isEqualTo("pedidos-inventario");
                          assertThat(log.path("environment").asString()).isEqualTo("local");
                          assertThat(log.path("machineName").asString()).isNotBlank();
                        }));
    assertThat(salida.getAll()).doesNotContain("has already been written");
  }
}
