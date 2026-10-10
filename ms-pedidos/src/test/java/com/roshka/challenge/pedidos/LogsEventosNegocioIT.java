package com.roshka.challenge.pedidos;

import static com.roshka.challenge.pedidos.ExportadorLogsEnMemoria.atributo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.contrato.MotivoRechazoStock;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.pedidos.aplicacion.Respuestas;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.CancelarPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ColocarPedido;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.time.Duration;
import java.util.List;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "management.opentelemetry.logging.export.schedule-delay=100ms")
@DirtiesContext
@AutoConfigureTracing
@Import(ExportadorLogsEnMemoria.Configuracion.class)
@ImportTestcontainers(ContenedoresCompartidos.class)
@ExtendWith(OutputCaptureExtension.class)
class LogsEventosNegocioIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired ColocarPedido colocarPedido;
  @Autowired CancelarPedido cancelarPedido;
  @Autowired ExportadorLogsEnMemoria exportador;
  @Autowired JsonMapper json;

  @Test
  @DisplayName("BON-03 · 010-AC-10 colocar un pedido dice qué se pidió")
  void debe_registrar_el_pedido_colocado_con_sus_lineas_y_unidades() {
    var primero = UUID.randomUUID();
    var segundo = UUID.randomUUID();

    var pedido =
        colocarPedido.colocar(
            List.of(new LineaPedido(primero, 2), new LineaPedido(segundo, 3)), "corr-neg");

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(registrosQueEmpiezanCon("Pedido " + pedido.id() + " colocado"))
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getBodyValue().asString())
                              .contains("2 líneas, 5 unidades")
                              .contains(primero + "×2")
                              .contains(segundo + "×3");
                          assertThat(atributo(r, "orderId")).isEqualTo(pedido.id().toString());
                          assertThat(atributo(r, "lines")).isEqualTo("2");
                          assertThat(atributo(r, "units")).isEqualTo("5");
                        }));
  }

  @Test
  @DisplayName(
      "BON-03 · 008-AC-06 colocar y confirmar un pedido deja rastro, con el mensaje publicado")
  void debe_registrar_los_eventos_de_negocio_cuando_se_coloca_y_se_confirma_un_pedido() {
    var pedido = colocarPedido.colocar(List.of(new LineaPedido(UUID.randomUUID(), 1)), "corr-neg");
    var respuesta = Respuestas.stockReservado(pedido.id());
    enviarAInventarioEventos(pedido.id(), json.writeValueAsString(respuesta));

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var id = pedido.id().toString();
              assertThat(registrosQueEmpiezanCon("Pedido " + id + " confirmado"))
                  .singleElement()
                  .satisfies(
                      r -> {
                        assertThat(r.getBodyValue().asString()).contains("saga completa");
                        assertThat(atributo(r, "sagaDurationMs")).isNotBlank();
                      });
              assertThat(registrosQueEmpiezanCon("Evento publicado: ReservarStock"))
                  .anySatisfy(
                      r -> {
                        assertThat(atributo(r, "orderId")).isEqualTo(id);
                        assertThat(atributo(r, "topic")).isEqualTo(Topicos.INVENTARIO_COMANDOS);
                        assertThat(atributo(r, "messageId")).isNotBlank();
                        assertThat(atributo(r, "correlationId")).isEqualTo("corr-neg");
                      });
            });
  }

  @Test
  @DisplayName("BON-03 · 010-AC-09 un pedido rechazado dice cuál fue, qué pedía y por qué")
  void debe_explicar_el_rechazo_del_pedido_cuando_llega_stock_rechazado() {
    var producto = UUID.randomUUID();
    var pedido = colocarPedido.colocar(List.of(new LineaPedido(producto, 5)), "corr-rechazo");
    var respuesta = Respuestas.stockRechazado(pedido.id(), MotivoRechazoStock.STOCK_INSUFICIENTE);
    enviarAInventarioEventos(pedido.id(), json.writeValueAsString(respuesta));

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(registrosQueEmpiezanCon("Pedido " + pedido.id() + " rechazado"))
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getBodyValue().asString())
                              .contains("stock insuficiente")
                              .contains("pedía " + producto + "×5");
                          assertThat(atributo(r, "reason")).isEqualTo("INSUFFICIENT_STOCK");
                          assertThat(atributo(r, "orderId")).isEqualTo(pedido.id().toString());
                        }));
  }

  @Test
  @DisplayName("BON-03 · 008-AC-06 cancelar un pedido registra la cancelación y la liberación")
  void debe_registrar_la_cancelacion_y_la_liberacion_de_stock_cuando_se_cancela_un_pedido() {
    var pedido = colocarPedido.colocar(List.of(new LineaPedido(UUID.randomUUID(), 1)), "corr-neg");

    cancelarPedido.cancelar(pedido.id(), "corr-cancel");

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var id = pedido.id().toString();
              assertThat(registrosQueEmpiezanCon("Pedido " + id + " cancelado")).hasSize(1);
              assertThat(
                      registrosQueEmpiezanCon(
                          "Liberación de stock solicitada para el pedido " + id))
                  .singleElement()
                  .satisfies(
                      r -> assertThat(atributo(r, "cause")).isEqualTo("CANCELADO_POR_CLIENTE"));
            });
  }

  @Test
  @DisplayName(
      "BON-03 · 008-AC-06 el evento de negocio dentro de un listener sale también en la consola")
  void debe_escribir_en_la_consola_el_evento_de_negocio_cuando_el_mdc_ya_trae_el_pedido(
      CapturedOutput salida) {
    var pedido = colocarPedido.colocar(List.of(new LineaPedido(UUID.randomUUID(), 1)), "corr-neg");
    var respuesta = Respuestas.stockReservado(pedido.id());
    enviarAInventarioEventos(pedido.id(), json.writeValueAsString(respuesta));

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(salida.getOut().lines().filter(l -> l.startsWith("{")))
                    .anySatisfy(
                        linea -> {
                          var log = json.readTree(linea);
                          assertThat(log.path("message").asString())
                              .startsWith("Pedido " + pedido.id() + " confirmado");
                          assertThat(log.path("orderId").asString())
                              .isEqualTo(pedido.id().toString());
                        }));
    assertThat(salida.getAll()).doesNotContain("has already been written");
  }

  @Test
  @DisplayName("BON-03 · 010-AC-05 cada respuesta se registra al recibirse, procesarse y finalizar")
  void
      debe_registrar_el_ciclo_de_vida_de_la_respuesta_con_sus_detalles_cuando_llega_stock_reservado() {
    var pedido = colocarPedido.colocar(List.of(new LineaPedido(UUID.randomUUID(), 1)), "corr-vida");
    var respuesta = Respuestas.stockReservado(pedido.id());
    enviarAInventarioEventos(pedido.id(), json.writeValueAsString(respuesta));

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var delEvento =
                  exportador.registros().stream()
                      .filter(
                          r -> respuesta.idMensaje().toString().equals(atributo(r, "messageId")))
                      .sorted(
                          java.util.Comparator.comparingLong(
                              LogRecordData::getObservedTimestampEpochNanos))
                      .toList();
              var mensajes = delEvento.stream().map(r -> r.getBodyValue().asString()).toList();
              var recibido = indice(mensajes, "Evento recibido: StockReservado");
              var procesando = indice(mensajes, "Procesando evento: StockReservado");
              var confirmado = indice(mensajes, "Pedido " + pedido.id() + " confirmado");
              var finalizado = indice(mensajes, "Evento finalizado: StockReservado");
              assertThat(recibido).isGreaterThanOrEqualTo(0);
              assertThat(procesando).isGreaterThan(recibido);
              assertThat(confirmado).isGreaterThan(procesando);
              assertThat(finalizado).isGreaterThan(confirmado);
              assertThat(mensajes.get(recibido))
                  .contains("del pedido " + pedido.id())
                  .contains(Topicos.INVENTARIO_EVENTOS)
                  .contains("partición")
                  .contains("offset");
              assertThat(mensajes.get(finalizado))
                  .contains("→ APLICADO")
                  .containsPattern("en \\d+ ms");
              var detalle = delEvento.get(confirmado);
              assertThat(atributo(detalle, "eventType")).isEqualTo("StockReservado");
              assertThat(atributo(detalle, "eventTopic")).isEqualTo(Topicos.INVENTARIO_EVENTOS);
              assertThat(atributo(detalle, "eventPartition")).isNotBlank();
              assertThat(atributo(detalle, "eventOffset")).isNotBlank();
              assertThat(atributo(detalle, "correlationId")).isEqualTo(respuesta.idCorrelacion());
            });
  }

  private static int indice(List<String> mensajes, String prefijo) {
    for (int i = 0; i < mensajes.size(); i++) {
      if (mensajes.get(i).startsWith(prefijo)) {
        return i;
      }
    }
    return -1;
  }

  private void enviarAInventarioEventos(UUID idPedido, String mensaje) {
    try (var broker =
        new SondaKafka(ContenedoresCompartidos.KAFKA.getBootstrapServers(), "unused")) {
      broker.enviar(Topicos.INVENTARIO_EVENTOS, idPedido.toString(), mensaje);
    }
  }

  private List<LogRecordData> registrosQueEmpiezanCon(String prefijo) {
    return exportador.registros().stream()
        .filter(r -> r.getBodyValue().asString().startsWith(prefijo))
        .toList();
  }
}
