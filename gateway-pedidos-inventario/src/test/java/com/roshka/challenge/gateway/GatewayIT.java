package com.roshka.challenge.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTracing
@Testcontainers
class GatewayIT {

  @Container
  @ServiceConnection(name = "redis")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.8-alpine").withExposedPorts(6379);

  static final BackendSimulado PEDIDO_1 = new BackendSimulado("order-1");
  static final BackendSimulado PEDIDO_2 = new BackendSimulado("order-2");
  static final BackendSimulado INVENTARIO = new BackendSimulado("inventory-1");

  @LocalServerPort int puerto;

  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();

  @DynamicPropertySource
  static void servidoresBackend(DynamicPropertyRegistry registro) {
    registro.add(
        "gateway.servidores-backend.ms-pedidos", () -> PEDIDO_1.url() + "," + PEDIDO_2.url());
    registro.add("gateway.servidores-backend.ms-inventario", INVENTARIO::url);
  }

  @AfterAll
  static void detenerBackends() {
    PEDIDO_1.close();
    PEDIDO_2.close();
    INVENTARIO.close();
  }

  @Test
  @DisplayName("BON-02 · ADR 0007 /orders va a ms-pedidos y /products a ms-inventario")
  void debe_enrutar_cada_ruta_a_su_servicio() throws Exception {
    var pedido = get("/orders/123");
    var stock = get("/products/abc/stock");

    assertThat(pedido.statusCode()).isEqualTo(200);
    assertThat(cuerpo(pedido).get("backend").asString()).startsWith("order-");
    assertThat(cuerpo(pedido).get("path").asString()).isEqualTo("/orders/123");
    assertThat(cuerpo(stock).get("backend").asString()).isEqualTo("inventory-1");
  }

  @Test
  @DisplayName("REQ-02 · ADR 0007 las solicitudes se reparten entre las réplicas de ms-pedidos")
  void debe_repartir_las_solicitudes_entre_todas_las_replicas() throws Exception {
    var servidoresBackend = new HashSet<String>();
    for (int i = 0; i < 10; i++) {
      servidoresBackend.add(cuerpo(get("/orders/" + i)).get("backend").asString());
    }

    assertThat(servidoresBackend).containsExactlyInAnyOrder("order-1", "order-2");
  }

  @Test
  @DisplayName("BON-03 · 006-AC-04 se genera un id de correlación si falta y se conserva si viene")
  void debe_generar_o_conservar_el_id_de_correlacion_y_reenviarlo() throws Exception {
    var generado = get("/orders/1");
    var conservado =
        http.send(
            HttpRequest.newBuilder(uri("/orders/1"))
                .header("X-Correlation-Id", "from-client")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    var idGenerado = generado.headers().firstValue("X-Correlation-Id").orElseThrow();
    assertThat(idGenerado).isNotBlank();
    assertThat(cuerpo(generado).get("correlationId").asString()).isEqualTo(idGenerado);
    assertThat(conservado.headers().allValues("X-Correlation-Id")).containsExactly("from-client");
    assertThat(generado.headers().allValues("X-Correlation-Id")).containsExactly(idGenerado);
    assertThat(cuerpo(conservado).get("correlationId").asString()).isEqualTo("from-client");
  }

  @Test
  @DisplayName("REQ-24 · 005 RN-01 una escritura sin Idempotency-Key se rechaza en el borde")
  void debe_rechazar_la_escritura_sin_clave_de_idempotencia_antes_de_llegar_al_servicio()
      throws Exception {
    var aciertosAntes = INVENTARIO.aciertos();

    var rechazado = post("/products", null);
    var aceptado = post("/products", "key-1");

    assertThat(rechazado.statusCode()).isEqualTo(400);
    assertThat(rechazado.headers().firstValue("Content-Type"))
        .hasValueSatisfying(tipo -> assertThat(tipo).contains("application/problem+json"));
    assertThat(cuerpo(rechazado).get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_MISSING");
    assertThat(aceptado.statusCode()).isEqualTo(200);
    assertThat(cuerpo(aceptado).get("idempotencyKey").asString()).isEqualTo("key-1");
    assertThat(INVENTARIO.aciertos()).isEqualTo(aciertosAntes + 1);
  }

  @Test
  @DisplayName("BON-03 · 006-AC-01 la traza empieza en el gateway y llega al servicio")
  void debe_reenviar_el_contexto_de_traza_al_servicio() throws Exception {
    var traceparent = cuerpo(get("/orders/traced")).get("traceparent").asString();

    assertThat(traceparent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");
  }

  private HttpResponse<String> get(String ruta) throws Exception {
    return http.send(
        HttpRequest.newBuilder(uri(ruta)).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String ruta, String claveIdempotencia) throws Exception {
    var constructor =
        HttpRequest.newBuilder(uri(ruta))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"));
    if (claveIdempotencia != null) {
      constructor.header("Idempotency-Key", claveIdempotencia);
    }
    return http.send(constructor.build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String ruta) {
    return URI.create("http://localhost:" + puerto + ruta);
  }

  private JsonNode cuerpo(HttpResponse<String> respuestaHttp) {
    return json.readTree(respuestaHttp.body());
  }
}
