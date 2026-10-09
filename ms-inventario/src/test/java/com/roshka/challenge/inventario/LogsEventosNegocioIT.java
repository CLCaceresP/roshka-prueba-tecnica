package com.roshka.challenge.inventario;

import static com.roshka.challenge.inventario.ExportadorLogsEnMemoria.atributo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.LineaStock;
import com.roshka.challenge.contrato.MotivoLiberacion;
import com.roshka.challenge.contrato.ReservarStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.RegistrarProducto;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.time.Duration;
import java.time.Instant;
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

  @Autowired RegistrarProducto registrarProducto;
  @Autowired ExportadorLogsEnMemoria exportador;
  @Autowired JsonMapper json;

  @Test
  @DisplayName("BON-03 · 008-AC-06 registrar un producto dice cuál es y con cuánto stock")
  void debe_registrar_el_evento_de_negocio_cuando_se_registra_un_producto() {
    var nombre = NombresUnicos.de("registrado");
    var producto = registrarProducto.registrar(nombre, 5);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(registrosQueEmpiezanCon("Producto " + producto.id() + " registrado"))
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getBodyValue().asString())
                              .contains("\"" + nombre + "\"")
                              .contains("5 unidades disponibles");
                          assertThat(atributo(r, "productId")).isEqualTo(producto.id().toString());
                          assertThat(atributo(r, "initialQuantity")).isEqualTo("5");
                        }));
  }

  @Test
  @DisplayName("BON-03 · 008-AC-06 reservar y liberar dejan rastro con el pedido y lo que pasó")
  void debe_registrar_la_reserva_aprobada_y_el_stock_liberado_cuando_llegan_los_comandos(
      CapturedOutput salida) {
    var idPedido = UUID.randomUUID();
    var producto = registrarProducto.registrar(NombresUnicos.de("reservado"), 5);
    enviar(idPedido, reservar(idPedido, producto.id(), 2));
    enviar(idPedido, liberar(idPedido));

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(registrosQueEmpiezanCon("Reserva del pedido " + idPedido + " aprobada"))
                  .singleElement()
                  .satisfies(
                      r -> {
                        assertThat(r.getBodyValue().asString())
                            .contains("1 líneas, 2 unidades")
                            .contains(producto.id() + "×2");
                        assertThat(atributo(r, "result")).isEqualTo("RESERVED");
                      });
              assertThat(registrosQueEmpiezanCon("Stock del pedido " + idPedido + " liberado"))
                  .singleElement()
                  .satisfies(
                      r -> {
                        assertThat(r.getBodyValue().asString())
                            .contains("se devolvieron 2 unidades");
                        assertThat(atributo(r, "outcome")).isEqualTo("DEVUELTO");
                      });
              assertThat(registrosQueEmpiezanCon("Evento publicado: "))
                  .anySatisfy(
                      r -> {
                        assertThat(atributo(r, "orderId")).isEqualTo(idPedido.toString());
                        assertThat(atributo(r, "topic")).isEqualTo(Topicos.INVENTARIO_EVENTOS);
                        assertThat(atributo(r, "messageId")).isNotBlank();
                        assertThat(atributo(r, "correlationId")).isEqualTo("corr-" + idPedido);
                      });
            });
    assertThat(salida.getAll()).doesNotContain("has already been written");
  }

  @Test
  @DisplayName(
      "BON-03 · 010-AC-09 una reserva rechazada dice qué producto, cuánto pedía y cuánto había")
  void debe_explicar_el_rechazo_con_lo_pedido_y_el_stock_disponible_cuando_no_alcanza() {
    var idPedido = UUID.randomUUID();
    var producto = registrarProducto.registrar(NombresUnicos.de("escaso"), 3);
    enviar(idPedido, reservar(idPedido, producto.id(), 5));

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(registrosQueEmpiezanCon("Reserva del pedido " + idPedido + " rechazada"))
                    .singleElement()
                    .satisfies(
                        r -> {
                          assertThat(r.getBodyValue().asString())
                              .isEqualTo(
                                  "Reserva del pedido %s rechazada por stock insuficiente: producto %s pedía 5 y había 3 disponibles"
                                      .formatted(idPedido, producto.id()));
                          assertThat(atributo(r, "result")).isEqualTo("REJECTED");
                          assertThat(atributo(r, "reason")).isEqualTo("INSUFFICIENT_STOCK");
                          assertThat(atributo(r, "productId")).isEqualTo(producto.id().toString());
                          assertThat(atributo(r, "requested")).isEqualTo("5");
                          assertThat(atributo(r, "available")).isEqualTo("3");
                        }));
  }

  @Test
  @DisplayName("BON-03 · 010-AC-05 cada evento se registra al recibirse, procesarse y finalizar")
  void debe_registrar_el_ciclo_de_vida_del_evento_con_sus_detalles_cuando_llega_un_comando() {
    var idPedido = UUID.randomUUID();
    var producto = registrarProducto.registrar(NombresUnicos.de("ciclo"), 3);
    var comando = reservar(idPedido, producto.id(), 5);
    enviar(idPedido, comando);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var delEvento =
                  exportador.registros().stream()
                      .filter(r -> comando.idMensaje().toString().equals(atributo(r, "messageId")))
                      .sorted(
                          java.util.Comparator.comparingLong(
                              LogRecordData::getObservedTimestampEpochNanos))
                      .map(r -> r.getBodyValue().asString())
                      .toList();
              var recibido = indice(delEvento, "Evento recibido: ReservarStock");
              var procesando = indice(delEvento, "Procesando evento: ReservarStock");
              var paso = indice(delEvento, "Mensaje marcado como procesado");
              var finalizado = indice(delEvento, "Evento finalizado: ReservarStock");
              assertThat(recibido).isGreaterThanOrEqualTo(0);
              assertThat(procesando).isGreaterThan(recibido);
              assertThat(paso).isGreaterThan(procesando);
              assertThat(finalizado).isGreaterThan(paso);
              assertThat(delEvento.get(recibido))
                  .contains("del pedido " + idPedido)
                  .contains(Topicos.INVENTARIO_COMANDOS)
                  .contains("partición")
                  .contains("offset")
                  .contains(producto.id() + "×5");
              assertThat(delEvento.get(finalizado))
                  .contains("del pedido " + idPedido)
                  .contains("→ APLICADO")
                  .containsPattern("en \\d+ ms");
            });
    var paso =
        exportador.registros().stream()
            .filter(r -> r.getBodyValue().asString().startsWith("Mensaje marcado como procesado"))
            .filter(r -> comando.idMensaje().toString().equals(atributo(r, "messageId")))
            .findFirst()
            .orElseThrow();
    assertThat(atributo(paso, "eventType")).isEqualTo("ReservarStock");
    assertThat(atributo(paso, "eventTopic")).isEqualTo(Topicos.INVENTARIO_COMANDOS);
    assertThat(atributo(paso, "eventPartition")).isNotBlank();
    assertThat(atributo(paso, "eventOffset")).isNotBlank();
    assertThat(atributo(paso, "orderId")).isEqualTo(idPedido.toString());
    assertThat(atributo(paso, "correlationId")).isEqualTo("corr-" + idPedido);
  }

  private static int indice(List<String> mensajes, String prefijo) {
    for (int i = 0; i < mensajes.size(); i++) {
      if (mensajes.get(i).startsWith(prefijo)) {
        return i;
      }
    }
    return -1;
  }

  private void enviar(UUID idPedido, Object mensaje) {
    try (var broker =
        new SondaKafka(ContenedoresCompartidos.KAFKA.getBootstrapServers(), "unused")) {
      broker.enviar(
          Topicos.INVENTARIO_COMANDOS, idPedido.toString(), json.writeValueAsString(mensaje));
    }
  }

  private List<LogRecordData> registrosQueEmpiezanCon(String prefijo) {
    return exportador.registros().stream()
        .filter(r -> r.getBodyValue().asString().startsWith(prefijo))
        .toList();
  }

  private static SobreMensaje<ReservarStock> reservar(
      UUID idPedido, UUID idProducto, int cantidad) {
    return new SobreMensaje<>(
        UUID.randomUUID(),
        TiposMensaje.RESERVAR_STOCK,
        TiposMensaje.VERSION_ESQUEMA,
        Instant.now(),
        "corr-" + idPedido,
        null,
        idPedido,
        new ReservarStock(idPedido, List.of(new LineaStock(idProducto, cantidad))));
  }

  private static SobreMensaje<LiberarStock> liberar(UUID idPedido) {
    return new SobreMensaje<>(
        UUID.randomUUID(),
        TiposMensaje.LIBERAR_STOCK,
        TiposMensaje.VERSION_ESQUEMA,
        Instant.now(),
        "corr-" + idPedido,
        null,
        idPedido,
        new LiberarStock(idPedido, MotivoLiberacion.CANCELADO_POR_CLIENTE));
  }
}
