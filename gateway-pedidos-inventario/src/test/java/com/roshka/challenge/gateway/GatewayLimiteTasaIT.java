package com.roshka.challenge.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.logs.Severity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "gateway.limite-tasa.tasa-reposicion=1",
      "gateway.limite-tasa.capacidad-rafaga=2",
      "management.opentelemetry.logging.export.schedule-delay=100ms"
    })
@AutoConfigureTracing
@Import(ExportadorLogsEnMemoria.Configuracion.class)
@Testcontainers
class GatewayLimiteTasaIT {

  private static final int SOLICITUDES = 10;

  @Container
  @ServiceConnection(name = "redis")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.8-alpine").withExposedPorts(6379);

  static final BackendSimulado PEDIDOS = new BackendSimulado("order-1");

  @LocalServerPort int puerto;
  @Autowired ExportadorLogsEnMemoria exportador;

  @DynamicPropertySource
  static void servidoresBackend(DynamicPropertyRegistry registro) {
    registro.add("gateway.servidores-backend.ms-pedidos", PEDIDOS::url);
    registro.add("gateway.servidores-backend.ms-inventario", PEDIDOS::url);
  }

  @AfterAll
  static void detenerBackend() {
    PEDIDOS.close();
  }

  @Test
  @DisplayName("BON-02 · ADR 0007 un cliente que supera su tasa recibe 429 sin llegar al servicio")
  void debe_responder_demasiadas_solicitudes_cuando_el_cliente_excede_su_tasa() throws Exception {
    var http = HttpClient.newHttpClient();
    var estados = new ArrayList<Integer>();

    for (int i = 0; i < SOLICITUDES; i++) {
      var solicitud =
          HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/orders/" + i))
              .GET()
              .build();
      estados.add(http.send(solicitud, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    assertThat(estados).contains(200, 429);
    assertThat(PEDIDOS.aciertos()).isLessThan(SOLICITUDES);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(exportador.registros())
                    .anySatisfy(
                        r -> {
                          assertThat(r.getBodyValue().asString())
                              .startsWith("Solicitud HTTP procesada: GET /orders/")
                              .contains("respondió 429")
                              .contains("límite de solicitudes por cliente");
                          assertThat(r.getSeverity()).isEqualTo(Severity.WARN);
                        }));
  }
}
