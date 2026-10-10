package com.roshka.challenge.sistema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ComposeContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests de caja negra contra todo el stack de docker compose: dos instancias de ms-pedidos y dos de
 * ms-inventario, Postgres, Kafka y Redis. Las solicitudes se alternan entre las réplicas de
 * pedidos. Requiere que los puertos publicados de docker-compose.yml estén libres.
 */
@Tag("e2e")
@Tag("concurrency")
class PedidoInventarioExtremoAExtremoIT {

  private static final List<String> REPLICAS_PEDIDOS =
      List.of("http://localhost:8081", "http://localhost:8082");
  private static final String INVENTARIO = "http://localhost:8091";
  private static final Duration TIMEOUT_SAGA = Duration.ofSeconds(90);
  private static final int PEDIDOS = 100;
  private static final int ULTIMAS_UNIDADES = 10;
  private static final int PEDIDOS_CANCELADOS = 20;

  private static final ComposeContainer STACK =
      new ComposeContainer(new File("../docker-compose.yml"))
          .withBuild(true)
          .withEnv("COMPOSE_PROFILES", "full");

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @BeforeAll
  static void iniciarStack() {
    STACK.start();
    var readiness =
        List.of(
            "http://localhost:8081",
            "http://localhost:8082",
            "http://localhost:8091",
            "http://localhost:8092");
    await()
        .atMost(Duration.ofMinutes(5))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> readiness.stream().allMatch(PedidoInventarioExtremoAExtremoIT::estaListo));
  }

  private static boolean estaListo(String urlBase) {
    try {
      return get(urlBase + "/actuator/health/readiness").statusCode() == 200;
    } catch (Exception aunNoLevantado) {
      return false;
    }
  }

  @AfterAll
  static void detenerStack() {
    STACK.stop();
  }

  @Test
  @DisplayName("REQ-13 REQ-31 · escenario 1: 100 pedidos concurrentes por las últimas 10 unidades")
  void debe_confirmar_exactamente_las_unidades_disponibles_cuando_compiten_100_pedidos()
      throws Exception {
    var producto = registrarProducto(ULTIMAS_UNIDADES);

    var idsPedido =
        ejecutarConcurrentemente(
            PEDIDOS, i -> () -> colocarPedido(REPLICAS_PEDIDOS.get(i % 2), producto, 1));

    var estadosFinales = esperarTerminal(idsPedido);
    assertThat(estadosFinales.get("CONFIRMED")).isEqualTo(ULTIMAS_UNIDADES);
    assertThat(estadosFinales.get("REJECTED")).isEqualTo(PEDIDOS - ULTIMAS_UNIDADES);
    var stock = stockDe(producto);
    assertThat(stock.get("available").asInt()).isZero();
    assertThat(stock.get("reserved").asInt()).isEqualTo(ULTIMAS_UNIDADES);
    assertThat(stock.get("total").asInt()).isEqualTo(ULTIMAS_UNIDADES);
  }

  @Test
  @DisplayName("REQ-27 · 004-AC-01/02 pedidos cancelados mientras su reserva está en vuelo")
  void debe_devolver_todo_el_stock_cuando_los_pedidos_se_cancelan_durante_la_reserva()
      throws Exception {
    var producto = registrarProducto(PEDIDOS_CANCELADOS);

    var idsPedido =
        ejecutarConcurrentemente(
            PEDIDOS_CANCELADOS,
            i -> {
              var replica = REPLICAS_PEDIDOS.get(i % 2);
              var otro = REPLICAS_PEDIDOS.get((i + 1) % 2);
              return () -> {
                var idPedido = colocarPedido(replica, producto, 1);
                cancelarHastaContestacion(otro, idPedido);
                return idPedido;
              };
            });

    assertThat(esperarTerminal(idsPedido)).containsOnlyKeys("CANCELLED");
    await()
        .atMost(TIMEOUT_SAGA)
        .untilAsserted(
            () -> {
              var stock = stockDe(producto);
              assertThat(stock.get("available").asInt()).isEqualTo(PEDIDOS_CANCELADOS);
              assertThat(stock.get("reserved").asInt()).isZero();
            });
  }

  private static UUID registrarProducto(int cantidad) throws Exception {
    var respuestaHttp =
        post(
            INVENTARIO + "/products",
            "{\"name\":\"e2e-%s\",\"initialQuantity\":%d}".formatted(UUID.randomUUID(), cantidad));
    assertThat(respuestaHttp.statusCode()).isEqualTo(201);
    return UUID.fromString(JSON.readTree(respuestaHttp.body()).get("productId").asString());
  }

  private static UUID colocarPedido(String replica, UUID producto, int cantidad) throws Exception {
    var respuestaHttp =
        post(
            replica + "/orders",
            "{\"lines\":[{\"productId\":\"%s\",\"quantity\":%d}]}".formatted(producto, cantidad));
    assertThat(respuestaHttp.statusCode()).isEqualTo(202);
    return UUID.fromString(JSON.readTree(respuestaHttp.body()).get("orderId").asString());
  }

  private static void cancelarHastaContestacion(String replica, UUID idPedido) {
    var clave = UUID.randomUUID().toString();
    await()
        .atMost(TIMEOUT_SAGA)
        .pollInterval(Duration.ofMillis(50))
        .until(
            () -> delete(replica + "/orders/" + idPedido, clave).statusCode(),
            estado -> estado == 200);
  }

  private static Map<String, Long> esperarTerminal(List<UUID> idsPedido) {
    return await()
        .atMost(TIMEOUT_SAGA)
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () ->
                idsPedido.stream()
                    .map(id -> estadoDe(id))
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())),
            estados -> !estados.containsKey("PENDING"))
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static String estadoDe(UUID idPedido) {
    try {
      return JSON.readTree(get(REPLICAS_PEDIDOS.getFirst() + "/orders/" + idPedido).body())
          .get("status")
          .asString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static JsonNode stockDe(UUID producto) throws Exception {
    return JSON.readTree(get(INVENTARIO + "/products/" + producto + "/stock").body());
  }

  private static HttpResponse<String> delete(String url, String claveIdempotencia)
      throws Exception {
    var solicitud =
        HttpRequest.newBuilder(URI.create(url))
            .header("Idempotency-Key", claveIdempotencia)
            .DELETE()
            .build();
    return HTTP.send(solicitud, HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> post(String url, String cuerpo) throws Exception {
    return post(url, cuerpo, UUID.randomUUID().toString());
  }

  private static HttpResponse<String> post(String url, String cuerpo, String claveIdempotencia)
      throws Exception {
    var solicitud =
        HttpRequest.newBuilder(URI.create(url))
            .header("Idempotency-Key", claveIdempotencia)
            .header("Content-Type", "application/json")
            .POST(
                cuerpo == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(cuerpo))
            .build();
    return HTTP.send(solicitud, HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> get(String url) throws Exception {
    return HTTP.send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private interface FabricaTareas<T> {
    Callable<T> tarea(int indice);
  }

  private static <T> List<T> ejecutarConcurrentemente(int hilos, FabricaTareas<T> fabrica)
      throws Exception {
    var iniciar = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(hilos)) {
      var futuros = new ArrayList<Future<T>>();
      for (int i = 0; i < hilos; i++) {
        var tarea = fabrica.tarea(i);
        futuros.add(
            ejecutor.submit(
                () -> {
                  iniciar.await();
                  return tarea.call();
                }));
      }
      iniciar.countDown();
      var resultados = new ArrayList<T>();
      for (var futuro : futuros) {
        resultados.add(futuro.get(3, TimeUnit.MINUTES));
      }
      return resultados;
    }
  }
}
