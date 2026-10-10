package com.roshka.challenge.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.pedidos.aplicacion.Respuestas;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ColocarPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ExpirarSagasEstancadas;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ManejarRespuestaInventario;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.BloqueoNoAdquirido;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Los escenarios de concurrencia corren contra dos instancias de ms-pedidos (el contexto de este
 * test y un segundo contexto de aplicación) que comparten la misma base, el mismo broker y el mismo
 * Redis. Los hilos arrancan juntos en un latch; las aserciones son solo sobre el estado final.
 */
abstract class EscenariosConcurrenciaPedido {

  private static final int SOLICITUDES_MISMA_CLAVE = 50;
  private static final int PEDIDOS_EN_CARRERA = 50;
  private static final int RESPUESTAS_PARA_DOS_CONSUMIDORES = 50;
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  static SegundaInstancia segundaInstancia;

  @LocalServerPort int puerto;
  @Autowired ColocarPedido colocarPedido;
  @Autowired ManejarRespuestaInventario respuestas;
  @Autowired PedidoRepository pedidos;
  @Autowired TransactionTemplate tx;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;
  @Autowired KafkaListenerEndpointRegistry listeners;

  private final HttpClient http = HttpClient.newHttpClient();

  /**
   * Arranca la otra instancia; los contenedores solo están levantados una vez que cargó el contexto
   * de este test.
   */
  abstract SegundaInstancia iniciarSegundaInstancia();

  /**
   * Respuestas permitidas a los duplicados concurrentes: 409 + Retry-After mientras se retiene el
   * bloqueo de la clave, o solo el 202 reproducido cuando no hay bloqueo y la clave primaria los
   * serializa.
   */
  abstract Set<Integer> estadosDuplicadosConcurrentes();

  @BeforeEach
  void asegurarSegundaInstancia() {
    if (segundaInstancia == null) {
      segundaInstancia = iniciarSegundaInstancia();
    }
  }

  @AfterAll
  static void detenerSegundaInstancia() {
    if (segundaInstancia != null) {
      segundaInstancia.close();
      segundaInstancia = null;
    }
  }

  @Test
  @DisplayName(
      "REQ-24 REQ-25 REQ-31 · 005-AC-06/07 50 POST concurrentes con una misma clave, 2 instancias")
  void debe_crear_un_pedido_cuando_la_misma_clave_se_envia_concurrentemente_a_ambas_instancias()
      throws Exception {
    var clave = UUID.randomUUID().toString();
    var producto = UUID.randomUUID();
    var cuerpo = "{\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}".formatted(producto);

    var primerasContestaciones =
        ejecutarConcurrentemente(
            SOLICITUDES_MISMA_CLAVE, i -> () -> post(urlInstancia(i) + "/orders", clave, cuerpo));

    assertThat(primerasContestaciones)
        .extracting(HttpResponse::statusCode)
        .allSatisfy(estado -> assertThat(estado).isIn(estadosDuplicadosConcurrentes()));
    var cuerposFinales = new ArrayList<String>();
    for (int i = 0; i < SOLICITUDES_MISMA_CLAVE; i++) {
      var url = urlInstancia(i) + "/orders";
      cuerposFinales.add(
          await()
              .atMost(TIMEOUT)
              .until(
                  () -> post(url, clave, cuerpo),
                  respuestaHttp -> respuestaHttp.statusCode() == 202)
              .body());
    }
    assertThat(cuerposFinales).containsOnly(cuerposFinales.getFirst());
    assertThat(contar("select count(*) from order_lines where product_id = :id", producto))
        .isEqualTo(1);
    var idPedido =
        UUID.fromString(json.readTree(cuerposFinales.getFirst()).get("orderId").asString());
    assertThat(
            contar(
                "select count(*) from outbox where aggregate_id = :id and type = 'ReservarStock'",
                idPedido))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "REQ-25 REQ-27 · 004-AC-02 una cancelación compite con StockReservado entre instancias")
  void debe_terminar_cancelado_con_una_liberacion_cuando_cancelar_compite_con_stock_reservado()
      throws Exception {
    var enCarrera = colocarPedidos(PEDIDOS_EN_CARRERA, Instant.now());

    ejecutarConcurrentemente(
        PEDIDOS_EN_CARRERA * 2,
        i -> {
          var idPedido = enCarrera.get(i / 2);
          return i % 2 == 0
              ? () ->
                  reintentarHastaAplicar(
                      () -> respuestas.alStockReservado(Respuestas.stockReservado(idPedido)))
              : () -> cancelarHastaContestacion(urlInstancia(i), idPedido);
        });

    for (var idPedido : enCarrera) {
      assertThat(estadoDe(idPedido)).isEqualTo("CANCELLED");
      assertThat(motivosLiberacionDe(idPedido))
          .containsOnlyOnce("CANCELADO_POR_CLIENTE")
          .hasSizeBetween(1, 2)
          .allSatisfy(
              motivo ->
                  assertThat(motivo).isIn("CANCELADO_POR_CLIENTE", "COMPENSACION_RESERVA_TARDIA"));
    }
  }

  @Test
  @DisplayName(
      "REQ-27 · 004-AC-07 el timeout de la saga compite con StockReservado y nunca mezcla desenlaces")
  void
      debe_confirmar_o_expirar_cada_pedido_de_forma_consistente_cuando_el_timeout_compite_con_la_reserva()
          throws Exception {
    var estancado = colocarPedidos(PEDIDOS_EN_CARRERA, Instant.now().minus(Duration.ofHours(1)));
    var timeout = segundaInstancia.bean(ExpirarSagasEstancadas.class);

    ejecutarConcurrentemente(
        PEDIDOS_EN_CARRERA + 1,
        i ->
            i == PEDIDOS_EN_CARRERA
                ? timeout::expirarEstancadas
                : () ->
                    reintentarHastaAplicar(
                        () ->
                            respuestas.alStockReservado(
                                Respuestas.stockReservado(estancado.get(i)))));
    timeout.expirarEstancadas();

    for (var idPedido : estancado) {
      var motivos = motivosLiberacionDe(idPedido);
      switch (estadoDe(idPedido)) {
        case "CONFIRMED" -> assertThat(motivos).isEmpty();
        case "REJECTED" ->
            assertThat(motivos)
                .containsOnlyOnce("TIMEOUT_SAGA")
                .hasSizeBetween(1, 2)
                .allSatisfy(r -> assertThat(r).isIn("TIMEOUT_SAGA", "COMPENSACION_RESERVA_TARDIA"));
        default ->
            throw new AssertionError("el pedido " + idPedido + " no llegó a un estado final");
      }
    }
  }

  @Test
  @DisplayName(
      "REQ-02 REQ-14 · escenario 5: dos instancias consumidoras comparten el tópico de respuestas")
  void debe_aplicar_cada_respuesta_una_vez_cuando_dos_instancias_consumen_el_topico()
      throws Exception {
    var colocado = colocarPedidos(RESPUESTAS_PARA_DOS_CONSUMIDORES, Instant.now());
    var listenersSegunda = segundaInstancia.bean(KafkaListenerEndpointRegistry.class);
    await().atMost(TIMEOUT).until(() -> asignado(listeners) > 0 && asignado(listenersSegunda) > 0);

    try (var broker =
        new SondaKafka(ContenedoresCompartidos.KAFKA.getBootstrapServers(), "unused")) {
      for (var idPedido : colocado) {
        var respuesta = Respuestas.stockReservado(idPedido);
        broker.enviar(
            Topicos.INVENTARIO_EVENTOS, idPedido.toString(), json.writeValueAsString(respuesta));
        broker.enviar(
            Topicos.INVENTARIO_EVENTOS, idPedido.toString(), json.writeValueAsString(respuesta));
      }
    }

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(colocado)
                    .allSatisfy(id -> assertThat(estadoDe(id)).isEqualTo("CONFIRMED")));
    for (var idPedido : colocado) {
      assertThat(contar("select version from orders where id = :id", idPedido)).isEqualTo(1);
    }
  }

  private String urlInstancia(int i) {
    return i % 2 == 0 ? "http://localhost:" + puerto : segundaInstancia.urlBase();
  }

  private List<UUID> colocarPedidos(int contar, Instant colocadoEn) {
    var ids = new ArrayList<UUID>();
    for (int i = 0; i < contar; i++) {
      var pedido =
          Pedido.colocar(
              UUID.randomUUID(),
              List.of(new LineaPedido(UUID.randomUUID(), 1)),
              "corr-race",
              colocadoEn);
      pedido.extraerEventos();
      tx.executeWithoutResult(s -> pedidos.insertar(pedido));
      ids.add(pedido.id());
    }
    return ids;
  }

  /**
   * Lo que hace el error handler de Kafka: reintentar mientras el pedido está ocupado en otro lado.
   */
  private static Object reintentarHastaAplicar(Callable<?> paso) {
    return await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(20))
        .ignoreExceptionsMatching(
            e ->
                e instanceof BloqueoNoAdquirido
                    || e instanceof ObjectOptimisticLockingFailureException
                    || e instanceof DuplicateKeyException)
        .until(paso, resultado -> true);
  }

  /** Lo que hace un cliente tras un 409 CONCURRENT_MODIFICATION: reintentar con la misma clave. */
  private Object cancelarHastaContestacion(String urlBase, UUID idPedido) {
    var clave = UUID.randomUUID().toString();
    var url = urlBase + "/orders/" + idPedido;
    return await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(20))
        .until(() -> delete(url, clave), respuestaHttp -> respuestaHttp.statusCode() == 200);
  }

  private HttpResponse<String> delete(String url, String clave) throws Exception {
    var solicitud =
        HttpRequest.newBuilder(URI.create(url)).header("Idempotency-Key", clave).DELETE().build();
    return http.send(solicitud, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String url, String clave, String cuerpo) throws Exception {
    var solicitud =
        HttpRequest.newBuilder(URI.create(url))
            .header("Idempotency-Key", clave)
            .header("Content-Type", "application/json")
            .POST(
                cuerpo == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(cuerpo))
            .build();
    return http.send(solicitud, HttpResponse.BodyHandlers.ofString());
  }

  private String estadoDe(UUID idPedido) {
    return jdbc.sql("select status from orders where id = :id")
        .param("id", idPedido)
        .query(String.class)
        .single();
  }

  private List<String> motivosLiberacionDe(UUID idPedido) {
    return jdbc.sql(
            """
            select payload -> 'carga' ->> 'motivo' from outbox
             where aggregate_id = :id and type = 'LiberarStock' order by id
            """)
        .param("id", idPedido)
        .query(String.class)
        .list();
  }

  private long contar(String sql, UUID id) {
    return jdbc.sql(sql).param("id", id).query(Long.class).single();
  }

  private static int asignado(KafkaListenerEndpointRegistry registro) {
    return registro.getListenerContainers().stream()
        .mapToInt(
            contenedor ->
                contenedor.getAssignedPartitions() == null
                    ? 0
                    : contenedor.getAssignedPartitions().size())
        .sum();
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
        resultados.add(futuro.get(120, TimeUnit.SECONDS));
      }
      return resultados;
    }
  }
}
