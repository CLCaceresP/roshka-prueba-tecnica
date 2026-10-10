package com.roshka.challenge.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class DocumentacionGatewayIT {

  @Container
  @ServiceConnection(name = "redis")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.8-alpine").withExposedPorts(6379);

  static final BackendSimulado PEDIDOS = new BackendSimulado("order-1");
  static final BackendSimulado INVENTARIO = new BackendSimulado("inventory-1");

  @LocalServerPort int puerto;

  private final HttpClient http =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  private final JsonMapper json = JsonMapper.builder().build();

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
  @DisplayName("EXT-04 · 015-AC-05 el gateway entrega el documento OpenAPI de cada servicio")
  void debe_entregar_el_documento_de_cada_servicio_por_el_gateway() throws Exception {
    var pedidos = get("/docs/ms-pedidos/v3/api-docs");
    var inventario = get("/docs/ms-inventario/v3/api-docs");

    assertThat(pedidos.statusCode()).isEqualTo(200);
    assertThat(json.readTree(pedidos.body()).get("backend").asString()).startsWith("order-");
    assertThat(json.readTree(pedidos.body()).get("path").asString()).isEqualTo("/v3/api-docs");
    assertThat(inventario.statusCode()).isEqualTo(200);
    assertThat(json.readTree(inventario.body()).get("backend").asString()).isEqualTo("inventory-1");
    assertThat(json.readTree(inventario.body()).get("path").asString()).isEqualTo("/v3/api-docs");
  }

  @Test
  @DisplayName("EXT-04 · 015-AC-05 /docs abre el Swagger UI con las dos definiciones")
  void debe_abrir_el_swagger_ui_con_las_definiciones_de_ambos_servicios() throws Exception {
    var pagina = get("/docs");
    var configuracion = get("/v3/api-docs/swagger-config");

    assertThat(pagina.statusCode()).isEqualTo(200);
    assertThat(pagina.body()).containsIgnoringCase("swagger");
    assertThat(configuracion.statusCode()).isEqualTo(200);
    var urls = new java.util.TreeMap<String, String>();
    json.readTree(configuracion.body())
        .get("urls")
        .forEach(u -> urls.put(u.get("name").asString(), u.get("url").asString()));
    assertThat(urls)
        .containsExactlyInAnyOrderEntriesOf(
            java.util.Map.of(
                "ms-pedidos", "/docs/ms-pedidos/v3/api-docs",
                "ms-inventario", "/docs/ms-inventario/v3/api-docs"));
  }

  @Test
  @DisplayName("REQ-24 · 015-AC-06 probar un PUT o un DELETE sin clave desde el Swagger es 400")
  void debe_rechazar_el_put_y_el_delete_sin_clave_aunque_vengan_del_swagger() throws Exception {
    var put =
        http.send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:" + puerto + "/products/" + UUID.randomUUID() + "/stock"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"available\":1}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    var delete =
        http.send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + puerto + "/orders/" + UUID.randomUUID()))
                .DELETE()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    for (var respuesta : List.of(put, delete)) {
      assertThat(respuesta.statusCode()).isEqualTo(400);
      assertThat(json.readTree(respuesta.body()).get("code").asString())
          .isEqualTo("IDEMPOTENCY_KEY_MISSING");
    }
  }

  private HttpResponse<String> get(String ruta) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
