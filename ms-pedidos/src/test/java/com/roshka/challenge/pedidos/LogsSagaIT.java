package com.roshka.challenge.pedidos;

import static com.roshka.challenge.pedidos.ExportadorLogsEnMemoria.atributo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.pedidos.aplicacion.Respuestas;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ColocarPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ExpirarSagasEstancadas;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "management.opentelemetry.logging.export.schedule-delay=100ms")
@DirtiesContext
@AutoConfigureTracing
@Import(ExportadorLogsEnMemoria.Configuracion.class)
@ExtendWith(OutputCaptureExtension.class)
@ImportTestcontainers(ContenedoresCompartidos.class)
class LogsSagaIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired ColocarPedido colocarPedido;
  @Autowired ExpirarSagasEstancadas expirarSagasEstancadas;
  @Autowired PedidoRepository pedidos;
  @Autowired TransactionTemplate tx;
  @Autowired ExportadorLogsEnMemoria exportador;
  @Autowired JsonMapper json;

  @Test
  @DisplayName("BON-03 · 010-AC-05 aplicar una respuesta de inventario se lee paso a paso")
  void debe_registrar_los_pasos_de_aplicar_la_respuesta_con_el_bloqueo_y_los_tiempos(
      CapturedOutput salida) {
    var pedido = colocarPedido.colocar(List.of(new LineaPedido(UUID.randomUUID(), 2)), "corr-saga");
    var respuesta = Respuestas.stockReservado(pedido.id());

    try (var broker =
        new SondaKafka(ContenedoresCompartidos.KAFKA.getBootstrapServers(), "unused")) {
      broker.enviar(
          Topicos.INVENTARIO_EVENTOS, pedido.id().toString(), json.writeValueAsString(respuesta));
    }

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var delEvento =
                  exportador.registros().stream()
                      .filter(
                          r -> respuesta.idMensaje().toString().equals(atributo(r, "messageId")))
                      .sorted(
                          Comparator.comparingLong(LogRecordData::getObservedTimestampEpochNanos))
                      .map(r -> r.getBodyValue().asString())
                      .toList();
              var bloqueado = indice(delEvento, "Bloqueo order:" + pedido.id() + " adquirido en");
              var marcado = indice(delEvento, "Mensaje marcado como procesado");
              var cargado = indice(delEvento, "Pedido cargado");
              var transicion = indice(delEvento, "Transición aplicada al pedido");
              var actualizado = indice(delEvento, "Pedido actualizado y comandos registrados");
              var confirmado = indice(delEvento, "Pedido " + pedido.id() + " confirmado");
              var proceso = indice(delEvento, "Aplicar respuesta de inventario completado en");
              var liberado = indice(delEvento, "Bloqueo order:" + pedido.id() + " liberado tras");
              assertThat(bloqueado).isGreaterThanOrEqualTo(0);
              assertThat(marcado).isGreaterThan(bloqueado);
              assertThat(cargado).isGreaterThan(marcado);
              assertThat(transicion).isGreaterThan(cargado);
              assertThat(confirmado).isGreaterThan(transicion);
              assertThat(actualizado).isGreaterThan(confirmado);
              assertThat(proceso).isGreaterThan(actualizado);
              assertThat(liberado).isGreaterThan(proceso);
              assertThat(delEvento.get(transicion)).contains("PENDING").contains("CONFIRMED");
              assertThat(delEvento.get(bloqueado)).containsPattern("adquirido en \\d+ ms");
              assertThat(delEvento.get(liberado)).containsPattern("tras retenerlo \\d+ ms");
            });
    assertThat(salida.getAll()).doesNotContain("has already been written");
  }

  @Test
  @DisplayName("BON-03 · 010-AC-13 el job de timeout dice cuántos pedidos revisó y cuántos expiró")
  void debe_registrar_la_revision_de_sagas_estancadas_con_sus_cifras() {
    var estancado =
        Pedido.colocar(
            UUID.randomUUID(),
            List.of(new LineaPedido(UUID.randomUUID(), 1)),
            "corr-estancado-" + UUID.randomUUID(),
            Instant.now().minus(Duration.ofHours(1)));
    estancado.extraerEventos();
    tx.executeWithoutResult(s -> pedidos.insertar(estancado));

    expirarSagasEstancadas.expirarEstancadas();

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              var mensajes =
                  exportador.registros().stream().map(r -> r.getBodyValue().asString()).toList();
              assertThat(mensajes)
                  .anySatisfy(
                      m ->
                          assertThat(m)
                              .startsWith("Revisión de sagas estancadas:")
                              .containsPattern(
                                  "\\d+ pedidos expirados de \\d+ candidatos en \\d+ ms"));
              assertThat(mensajes)
                  .anyMatch(
                      m ->
                          m.startsWith(
                              "Pedido " + estancado.id() + " rechazado: se agotó el tiempo"));
              assertThat(mensajes).anyMatch(m -> m.startsWith("Expirar pedido completado en"));
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
}
