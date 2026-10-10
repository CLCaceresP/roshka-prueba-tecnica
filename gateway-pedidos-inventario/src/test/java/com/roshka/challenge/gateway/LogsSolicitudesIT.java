package com.roshka.challenge.gateway;

import static com.roshka.challenge.gateway.ExportadorLogsEnMemoria.atributo;
import static com.roshka.challenge.gateway.ExportadorLogsEnMemoria.recurso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.logs.Severity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "management.opentelemetry.logging.export.schedule-delay=100ms")
@AutoConfigureTracing
@Import(ExportadorLogsEnMemoria.Configuracion.class)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class LogsSolicitudesIT {

  private static final String MENSAJE = "Solicitud HTTP procesada";

  @Container
  @ServiceConnection(name = "redis")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.8-alpine").withExposedPorts(6379);

  static final BackendSimulado PEDIDOS = new BackendSimulado("order-1");
  static final BackendSimulado INVENTARIO = new BackendSimulado("inventory-1");

  @LocalServerPort int puerto;
  @Autowired ExportadorLogsEnMemoria exportador;

  private final HttpClient http = HttpClient.newHttpClient();

  @DynamicPropertySource
  static void servidoresBackend(DynamicPropertyRegistry registro) {
    registro.add("gateway.servidores-backend.ms-pedidos", PEDIDOS::url);
    registro.add("gateway.servidores-backend.ms-inventario", INVENTARIO::url);
  }

  @AfterAll
  static void detenerBackends() {
    PEDIDOS.close();
    INVENTARIO.close();
  }

  @Test
  @DisplayName("BON-03 · 008-AC-04 el gateway registra la solicitud, su ruta y su estado")
  void debe_registrar_la_solicitud_con_ruta_estado_y_correlacion_cuando_enruta() throws Exception {
    var idCorrelacion = "corr-gateway-" + System.nanoTime();

    var respuesta =
        http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/orders/123"))
                .header("X-Correlation-Id", idCorrelacion)
                .header("User-Agent", "agente-de-prueba")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(respuesta.statusCode()).isEqualTo(200);
    await()
        .atMost(Duration.ofSeconds(30))
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
                          assertThat(atributo(r, "path")).isEqualTo("/orders/123");
                          assertThat(atributo(r, "status")).isEqualTo("200");
                          assertThat(atributo(r, "routeId")).isEqualTo("orders");
                          assertThat(atributo(r, "durationMs")).isNotBlank();
                          assertThat(atributo(r, "clientIp")).isNotBlank();
                          assertThat(atributo(r, "userAgent")).isEqualTo("agente-de-prueba");
                          assertThat(atributo(r, "traceId")).isNotBlank();
                        }));
  }

  @Test
  @DisplayName("BON-03 · 010-AC-13 el gateway registra la entrada y la salida de forma legible")
  void debe_registrar_la_entrada_y_la_salida_con_ruta_destino_y_duracion_cuando_enruta()
      throws Exception {
    var idCorrelacion = "corr-legible-" + System.nanoTime();

    http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/orders/456"))
            .header("X-Correlation-Id", idCorrelacion)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              var mensajes =
                  exportador.registros().stream()
                      .filter(r -> idCorrelacion.equals(atributo(r, "correlationId")))
                      .map(r -> r.getBodyValue().asString())
                      .toList();
              assertThat(mensajes).contains("Solicitud HTTP recibida: GET /orders/456");
              assertThat(mensajes)
                  .anySatisfy(
                      m ->
                          assertThat(m)
                              .startsWith("Solicitud HTTP procesada: GET /orders/456 respondió 200")
                              .containsPattern("en \\d+ ms")
                              .contains("ruta orders")
                              .contains("/orders/456"));
            });
  }

  @Test
  @DisplayName("BON-03 · 010-AC-13 una escritura sin Idempotency-Key se rechaza y dice por qué")
  void debe_registrar_el_motivo_cuando_el_gateway_rechaza_una_escritura_sin_clave()
      throws Exception {
    var idCorrelacion = "corr-sin-clave-" + System.nanoTime();

    var respuesta =
        http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/orders"))
                .header("X-Correlation-Id", idCorrelacion)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(respuesta.statusCode()).isEqualTo(400);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(exportador.registros())
                    .filteredOn(
                        r ->
                            idCorrelacion.equals(atributo(r, "correlationId"))
                                && r.getBodyValue()
                                    .asString()
                                    .startsWith("Solicitud rechazada por el gateway"))
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getBodyValue().asString())
                              .isEqualTo(
                                  "Solicitud rechazada por el gateway: POST /orders sin el header Idempotency-Key (IDEMPOTENCY_KEY_MISSING)");
                          assertThat(r.getSeverity()).isEqualTo(Severity.WARN);
                          assertThat(atributo(r, "code")).isEqualTo("IDEMPOTENCY_KEY_MISSING");
                          assertThat(atributo(r, "status")).isEqualTo("400");
                        }));
  }

  @Test
  @DisplayName(
      "BON-03 · 008-AC-05 los logs exportados llevan los atributos de recurso de la aplicación")
  void debe_exportar_atributos_de_recurso_y_de_hilo_cuando_registra_un_log() throws Exception {
    http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/orders/1"))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(exportador.registros())
                    .anySatisfy(
                        r -> {
                          assertThat(recurso(r, "service.name"))
                              .isEqualTo("gateway-pedidos-inventario");
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
    http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/orders/1"))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(salida.getOut().lines().filter(l -> l.startsWith("{")))
                    .anySatisfy(
                        linea -> {
                          var log = JsonMapper.builder().build().readTree(linea);
                          assertThat(log.path("application").asString())
                              .isEqualTo("gateway-pedidos-inventario");
                          assertThat(log.path("group").asString()).isEqualTo("pedidos-inventario");
                          assertThat(log.path("environment").asString()).isEqualTo("local");
                          assertThat(log.path("machineName").asString()).isNotBlank();
                        }));
    assertThat(salida.getAll()).doesNotContain("has already been written");
  }
}
