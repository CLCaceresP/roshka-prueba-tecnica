package com.roshka.challenge.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.LineaStock;
import com.roshka.challenge.contrato.MotivoLiberacion;
import com.roshka.challenge.contrato.ReservarStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.AjustarStock;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ManejarLiberarStock;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ManejarReservarStock;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.RegistrarProducto;
import com.roshka.challenge.inventario.dominio.AjusteStock;
import com.roshka.challenge.inventario.dominio.ProductoDuplicado;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Llama a los puertos de entrada desde muchos hilos liberados en el mismo instante. Cada hilo hace
 * de consumidor independiente (otra instancia u otra partición); las aserciones son sobre el estado
 * final.
 */
@Tag("concurrency")
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@ImportTestcontainers(ContenedoresCompartidos.class)
class InventarioConcurrenciaIT {

  private static final int PEDIDOS = 100;
  private static final int ULTIMAS_UNIDADES = 10;
  private static final int PEDIDOS_CRUZADOS_POR_DIRECCION = 50;
  private static final int PEDIDOS_EN_CARRERA = 20;
  private static final int VALOR_INICIAL = 25;
  private static final int CAMBIOS_SIMULTANEOS = 50;
  private static final int STOCK_MEZCLA = 100;
  private static final int RESERVAS_MEZCLA = 40;
  private static final int CAMBIOS_MEZCLA = 30;
  private static final int LIBERACIONES_MEZCLA = 20;
  private static final int MINIMO_MEZCLA = 60;
  private static final int MISMA_CLAVE_SIMULTANEA = 20;

  @Autowired ManejarReservarStock reservarStock;
  @Autowired ManejarLiberarStock liberarStock;
  @Autowired RegistrarProducto registrarProducto;
  @Autowired AjustarStock ajustarStock;
  @Autowired JsonMapper json;
  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;

  @Test
  @DisplayName("REQ-13 REQ-31 · 100 pedidos concurrentes por las últimas 10 unidades")
  void debe_reservar_exactamente_las_unidades_disponibles_cuando_compiten_los_pedidos()
      throws Exception {
    var producto =
        registrarProducto.registrar(NombresUnicos.de("last-units"), ULTIMAS_UNIDADES).id();

    ejecutarConcurrentemente(
        PEDIDOS, i -> () -> reservarStock.manejar(reservar(UUID.randomUUID(), linea(producto, 1))));

    assertThat(reservasPara(producto, "RESERVED")).isEqualTo(ULTIMAS_UNIDADES);
    assertThat(reservasPara(producto, "REJECTED")).isEqualTo(PEDIDOS - ULTIMAS_UNIDADES);
    assertThat(stockDe(producto)).containsExactly(0, ULTIMAS_UNIDADES);
  }

  @Test
  @DisplayName(
      "REQ-11 REQ-31 · 001-AC-10 pedidos cruzados de varias líneas nunca producen deadlock")
  void debe_completar_todos_los_pedidos_cuando_las_lineas_se_piden_en_ordenes_opuestos()
      throws Exception {
    var a = registrarProducto.registrar(NombresUnicos.de("crossed-a"), 1000).id();
    var b = registrarProducto.registrar(NombresUnicos.de("crossed-b"), 1000).id();

    ejecutarConcurrentemente(
        PEDIDOS_CRUZADOS_POR_DIRECCION * 2,
        i ->
            () ->
                reservarStock.manejar(
                    i % 2 == 0
                        ? reservar(UUID.randomUUID(), linea(a, 1), linea(b, 2))
                        : reservar(UUID.randomUUID(), linea(b, 2), linea(a, 1))));

    assertThat(reservasPara(a, "RESERVED")).isEqualTo(PEDIDOS_CRUZADOS_POR_DIRECCION * 2);
    assertThat(stockDe(a)).containsExactly(900, 100);
    assertThat(stockDe(b)).containsExactly(800, 200);
  }

  @Test
  @DisplayName(
      "REQ-12 REQ-15 · 004-AC-11 liberaciones distintas y concurrentes devuelven el stock una sola vez")
  void debe_devolver_el_stock_una_vez_cuando_dos_liberaciones_compiten_por_el_mismo_pedido()
      throws Exception {
    var producto =
        registrarProducto.registrar(NombresUnicos.de("double-release"), PEDIDOS_EN_CARRERA).id();
    var pedidos = new ArrayList<UUID>();
    for (int i = 0; i < PEDIDOS_EN_CARRERA; i++) {
      var idPedido = UUID.randomUUID();
      reservarStock.manejar(reservar(idPedido, linea(producto, 1)));
      pedidos.add(idPedido);
    }

    ejecutarConcurrentemente(
        PEDIDOS_EN_CARRERA * 2, i -> () -> liberarStock.manejar(liberar(pedidos.get(i / 2))));

    assertThat(reservasPara(producto, "RELEASED")).isEqualTo(PEDIDOS_EN_CARRERA);
    assertThat(stockDe(producto)).containsExactly(PEDIDOS_EN_CARRERA, 0);
  }

  @Test
  @DisplayName("REQ-27 · 004-AC-05 ReservarStock y LiberarStock compiten por un pedido nuevo")
  void debe_dejar_el_stock_intacto_cuando_reservar_y_liberar_compiten_por_un_pedido_nuevo()
      throws Exception {
    var producto =
        registrarProducto
            .registrar(NombresUnicos.de("reserve-release-race"), PEDIDOS_EN_CARRERA)
            .id();
    var pedidos = new ArrayList<UUID>();
    for (int i = 0; i < PEDIDOS_EN_CARRERA; i++) {
      pedidos.add(UUID.randomUUID());
    }

    ejecutarConcurrentemente(
        PEDIDOS_EN_CARRERA * 2,
        i -> {
          var idPedido = pedidos.get(i / 2);
          var reservar = reservar(idPedido, linea(producto, 1));
          var liberar = liberar(idPedido);
          return i % 2 == 0
              ? () -> reintentandoClaveDuplicada(() -> reservarStock.manejar(reservar))
              : () -> reintentandoClaveDuplicada(() -> liberarStock.manejar(liberar));
        });

    assertThat(stockDe(producto)).containsExactly(PEDIDOS_EN_CARRERA, 0);
    assertThat(pedidos)
        .allSatisfy(idPedido -> assertThat(estadoReservaDe(idPedido)).isEqualTo("RELEASED"));
  }

  @Test
  @DisplayName(
      "REQ-22 REQ-31 · 011-AC-05 solicitudes simultáneas con el mismo nombre crean uno solo")
  void debe_crear_un_solo_producto_cuando_varias_solicitudes_usan_el_mismo_nombre()
      throws Exception {
    var nombre = NombresUnicos.de("simultaneo");
    var creados = new AtomicInteger();
    var repetidos = new AtomicInteger();

    ejecutarConcurrentemente(
        PEDIDOS_EN_CARRERA,
        i ->
            () -> {
              try {
                registrarProducto.registrar(nombre, 1);
                creados.incrementAndGet();
              } catch (ProductoDuplicado e) {
                repetidos.incrementAndGet();
              }
              return null;
            });

    assertThat(creados).hasValue(1);
    assertThat(repetidos).hasValue(PEDIDOS_EN_CARRERA - 1);
    assertThat(
            jdbc.sql("select count(*) from products where lower(name) = lower(:name)")
                .param("name", nombre)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "REQ-31 · 014-AC-08 50 cambios simultáneos dejan un valor y un historial que lo explica")
  void debe_dejar_un_historial_coherente_cuando_varios_cambios_llegan_a_la_vez() throws Exception {
    var producto = registrarProducto.registrar(NombresUnicos.de("puts"), VALOR_INICIAL).id();

    ejecutarConcurrentemente(
        CAMBIOS_SIMULTANEOS,
        i -> () -> ajustarStock.ajustar(AjusteStock.de(producto, i + 1, null)));

    var stock = stockCompletoDe(producto);
    assertThat(stock.available() + stock.reserved()).isEqualTo(stock.total());
    assertThat(stock.available()).isBetween(1, CAMBIOS_SIMULTANEOS);
    var sumaDeCambios =
        jdbc.sql(
                """
                select coalesce(sum(case when type = 'ADD' then quantity else -quantity end), 0)
                  from stock_movements where product_id = :id
                """)
            .param("id", producto)
            .query(Integer.class)
            .single();
    assertThat(sumaDeCambios).isEqualTo(stock.available() - VALOR_INICIAL);
  }

  @Test
  @DisplayName(
      "REQ-13 REQ-31 · 014-AC-09 reservas, liberaciones y cambios de stock concurrentes conservan el stock")
  void debe_conservar_el_stock_cuando_conviven_reservas_liberaciones_y_cambios_de_disponible()
      throws Exception {
    var producto =
        registrarProducto
            .registrar(NombresUnicos.de("mezcla"), STOCK_MEZCLA + LIBERACIONES_MEZCLA)
            .id();
    var pedidosAReservar = new ArrayList<UUID>();
    for (int i = 0; i < LIBERACIONES_MEZCLA; i++) {
      var idPedido = UUID.randomUUID();
      reservarStock.manejar(reservar(idPedido, linea(producto, 1)));
      pedidosAReservar.add(idPedido);
    }

    ejecutarConcurrentemente(
        RESERVAS_MEZCLA + CAMBIOS_MEZCLA + LIBERACIONES_MEZCLA,
        i -> {
          if (i < RESERVAS_MEZCLA) {
            return () -> reservarStock.manejar(reservar(UUID.randomUUID(), linea(producto, 1)));
          }
          if (i < RESERVAS_MEZCLA + LIBERACIONES_MEZCLA) {
            var idPedido = pedidosAReservar.get(i - RESERVAS_MEZCLA);
            return () -> liberarStock.manejar(liberar(idPedido));
          }
          var disponible = MINIMO_MEZCLA + (i - RESERVAS_MEZCLA - LIBERACIONES_MEZCLA);
          return () -> ajustarStock.ajustar(AjusteStock.de(producto, disponible, null));
        });

    var stock = stockCompletoDe(producto);
    assertThat(stock.available()).isGreaterThanOrEqualTo(0);
    assertThat(stock.reserved()).isEqualTo(RESERVAS_MEZCLA);
    assertThat(stock.available() + stock.reserved()).isEqualTo(stock.total());
    assertThat(reservasPara(producto, "RESERVED")).isEqualTo(RESERVAS_MEZCLA);
    assertThat(reservasPara(producto, "RELEASED")).isEqualTo(LIBERACIONES_MEZCLA);
  }

  @Test
  @DisplayName(
      "REQ-13 · 014-AC-03 dejar lo disponible en cero no cancela reservas y lo liberado después se suma")
  void debe_mantener_las_reservas_y_sumar_lo_liberado_cuando_se_deja_lo_disponible_en_cero() {
    var producto = registrarProducto.registrar(NombresUnicos.de("en-cero"), 5).id();
    var idPedido = UUID.randomUUID();
    reservarStock.manejar(reservar(idPedido, linea(producto, 2)));

    ajustarStock.ajustar(AjusteStock.de(producto, 0, null));

    assertThat(estadoReservaDe(idPedido)).isEqualTo("RESERVED");
    assertThat(stockDe(producto)).containsExactly(0, 2);

    liberarStock.manejar(liberar(idPedido));

    assertThat(estadoReservaDe(idPedido)).isEqualTo("RELEASED");
    assertThat(stockDe(producto)).containsExactly(2, 0);
    assertThat(stockCompletoDe(producto).total()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "REQ-24 REQ-31 · 014-AC-08 la misma clave repetida a la vez produce un solo movimiento")
  void debe_registrar_un_solo_movimiento_cuando_la_misma_clave_llega_varias_veces_a_la_vez()
      throws Exception {
    var producto = registrarProducto.registrar(NombresUnicos.de("misma-clave"), 5).id();
    var clave = UUID.randomUUID().toString();
    var respuestas = new ConcurrentLinkedQueue<JsonNode>();
    var reproducidas = new AtomicInteger();

    ejecutarConcurrentemente(
        MISMA_CLAVE_SIMULTANEA,
        i ->
            () -> {
              var respuesta =
                  mvc.put()
                      .uri("/products/{id}/stock", producto)
                      .header("Idempotency-Key", clave)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"available\":15}")
                      .exchange();
              var http = respuesta.getResponse();
              if ("true".equals(http.getHeader("Idempotent-Replayed"))) {
                reproducidas.incrementAndGet();
              }
              var cuerpo = json.readTree(http.getContentAsString(StandardCharsets.UTF_8));
              respuestas.add(
                  ((tools.jackson.databind.node.ObjectNode) cuerpo)
                      .put("httpStatus", http.getStatus()));
              return null;
            });

    var exitosas = respuestas.stream().filter(r -> r.get("httpStatus").asInt() == 200).toList();
    var enProceso = respuestas.stream().filter(r -> r.get("httpStatus").asInt() == 409).toList();
    assertThat(exitosas).isNotEmpty();
    assertThat(exitosas.size() + enProceso.size()).isEqualTo(MISMA_CLAVE_SIMULTANEA);
    assertThat(enProceso)
        .allSatisfy(
            r -> assertThat(r.get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_IN_PROGRESS"));
    assertThat(exitosas)
        .extracting(r -> r.path("movement").path("movementId").asString(""))
        .doesNotContain("")
        .containsOnly(exitosas.getFirst().path("movement").path("movementId").asString());
    assertThat(reproducidas.get()).isEqualTo(exitosas.size() - 1);
    assertThat(stockDe(producto)).containsExactly(15, 0);
    assertThat(movimientosDe(producto)).isEqualTo(1);
  }

  private int movimientosDe(UUID idProducto) {
    return jdbc.sql("select count(*) from stock_movements where product_id = :id")
        .param("id", idProducto)
        .query(Integer.class)
        .single();
  }

  private record StockCompleto(int total, int available, int reserved) {}

  private StockCompleto stockCompletoDe(UUID idProducto) {
    return jdbc.sql("select total, available, reserved from products where id = :id")
        .param("id", idProducto)
        .query(
            (rs, n) ->
                new StockCompleto(
                    rs.getInt("total"), rs.getInt("available"), rs.getInt("reserved")))
        .single();
  }

  private String estadoReservaDe(UUID idPedido) {
    return jdbc.sql("select status from reservations where order_id = :id")
        .param("id", idPedido)
        .query(String.class)
        .single();
  }

  /** El error handler de Kafka reintenta el insert perdedor; aquí lo hace el test (ADR 0010). */
  private static Object reintentandoClaveDuplicada(Callable<?> llamar) throws Exception {
    try {
      return llamar.call();
    } catch (DuplicateKeyException primerIntentoPerdioCarreraInsercion) {
      return llamar.call();
    }
  }

  private interface FabricaTareas {
    Callable<?> tarea(int indice);
  }

  private static void ejecutarConcurrentemente(int hilos, FabricaTareas fabrica) throws Exception {
    var iniciar = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(hilos)) {
      var futuros = new ArrayList<Future<?>>();
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
      for (var futuro : futuros) {
        futuro.get(60, TimeUnit.SECONDS);
      }
    }
  }

  private int reservasPara(UUID idProducto, String estado) {
    return jdbc.sql(
            """
            select count(*) from reservations r
              join reservation_lines l on l.order_id = r.order_id
             where l.product_id = :productId and r.status = :status
            """)
        .param("productId", idProducto)
        .param("status", estado)
        .query(Integer.class)
        .single();
  }

  private List<Integer> stockDe(UUID idProducto) {
    return jdbc.sql("select available, reserved from products where id = :id")
        .param("id", idProducto)
        .query((rs, n) -> List.of(rs.getInt("available"), rs.getInt("reserved")))
        .single();
  }

  private static LineaStock linea(UUID idProducto, int cantidad) {
    return new LineaStock(idProducto, cantidad);
  }

  private static SobreMensaje<ReservarStock> reservar(UUID idPedido, LineaStock... lineas) {
    return sobre(
        idPedido, TiposMensaje.RESERVAR_STOCK, new ReservarStock(idPedido, List.of(lineas)));
  }

  private static SobreMensaje<LiberarStock> liberar(UUID idPedido) {
    return sobre(
        idPedido,
        TiposMensaje.LIBERAR_STOCK,
        new LiberarStock(idPedido, MotivoLiberacion.CANCELADO_POR_CLIENTE));
  }

  private static <P> SobreMensaje<P> sobre(UUID idPedido, String tipo, P carga) {
    return new SobreMensaje<>(
        UUID.randomUUID(),
        tipo,
        TiposMensaje.VERSION_ESQUEMA,
        Instant.now(),
        "corr-" + idPedido,
        null,
        idPedido,
        carga);
  }
}
