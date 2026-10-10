package com.roshka.challenge.pedidos.adaptadores.entrada.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.contrato.MotivoRechazoStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.pedidos.ContenedoresCompartidos;
import com.roshka.challenge.pedidos.InyeccionFallos;
import com.roshka.challenge.pedidos.SondaKafka;
import com.roshka.challenge.pedidos.aplicacion.Respuestas;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ColocarPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ObtenerPedido;
import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.PedidoNoEncontrado;
import com.roshka.challenge.pedidos.dominio.TransicionPedidoInvalida;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@DirtiesContext
@Import(InyeccionFallos.class)
@ImportTestcontainers(ContenedoresCompartidos.class)
class PedidoResilienciaIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static final String DLT = Topicos.INVENTARIO_EVENTOS + Topicos.SUFIJO_MENSAJE_MUERTO;

  @Autowired InyeccionFallos.Fallos fallos;
  @Autowired ColocarPedido colocarPedido;
  @Autowired ObtenerPedido obtenerPedido;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;
  @Autowired MeterRegistry medidores;

  private SondaKafka inventario;
  private SondaKafka mensajesMuertos;

  @BeforeEach
  void abrirSondas() {
    inventario =
        new SondaKafka(
            ContenedoresCompartidos.KAFKA.getBootstrapServers(), Topicos.INVENTARIO_EVENTOS);
    mensajesMuertos = new SondaKafka(ContenedoresCompartidos.KAFKA.getBootstrapServers(), DLT);
  }

  @AfterEach
  void cerrarSondas() {
    inventario.close();
    mensajesMuertos.close();
  }

  @Test
  @DisplayName(
      "REQ-26 · 004-AC-12 StockRechazado sobre un pedido CONFIRMED va a la DLT sin cambios")
  void debe_enviar_a_dlt_la_respuesta_imposible_y_mantener_el_pedido_confirmado() {
    var mensajesMuertosAntes =
        medidores.counter("messages.dlt", "topic", Topicos.INVENTARIO_EVENTOS).count();
    var pedido = colocarPedido.colocar(List.of(new LineaPedido(UUID.randomUUID(), 1)), "corr-dlt");
    respuesta(Respuestas.stockReservado(pedido.id()));
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(obtenerPedido.get(pedido.id()).estado())
                    .isEqualTo(EstadoPedido.CONFIRMED));

    respuesta(Respuestas.stockRechazado(pedido.id(), MotivoRechazoStock.STOCK_INSUFICIENTE));

    var muerto = esperarMensajeMuerto(pedido.id().toString());
    assertThat(causaDe(muerto)).isEqualTo(TransicionPedidoInvalida.class.getName());
    assertThat(obtenerPedido.get(pedido.id()).estado()).isEqualTo(EstadoPedido.CONFIRMED);
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        medidores
                            .counter("messages.dlt", "topic", Topicos.INVENTARIO_EVENTOS)
                            .count())
                    .isEqualTo(mensajesMuertosAntes + 1));
  }

  @Test
  @DisplayName("REQ-26 · una respuesta para un pedido que no existe va a la DLT")
  void debe_enviar_a_dlt_la_respuesta_de_un_pedido_desconocido() {
    var desconocido = UUID.randomUUID();

    respuesta(Respuestas.stockReservado(desconocido));

    assertThat(causaDe(esperarMensajeMuerto(desconocido.toString())))
        .isEqualTo(PedidoNoEncontrado.class.getName());
  }

  @Test
  @DisplayName("REQ-18 · 003-AC-12 una respuesta malformada va a la DLT sin reintentos")
  void debe_enviar_a_dlt_la_respuesta_malformada() {
    var clave = UUID.randomUUID().toString();

    inventario.enviar(
        Topicos.INVENTARIO_EVENTOS, clave, "{\"versionEsquema\":1,\"tipo\":\"Nope\"}");

    assertThat(causaDe(esperarMensajeMuerto(clave))).isEqualTo(MensajeMalformado.class.getName());
  }

  @Test
  @DisplayName("REQ-06 · 002-AC-03 un fallo al escribir el outbox no deja ningún pedido")
  void debe_no_persistir_nada_cuando_falla_la_escritura_del_outbox() {
    var producto = UUID.randomUUID();
    fallos.fallarAlAgregarConCorrelacion("corr-rollback", 1);

    assertThatThrownBy(
            () -> colocarPedido.colocar(List.of(new LineaPedido(producto, 1)), "corr-rollback"))
        .isInstanceOf(TransientDataAccessResourceException.class);

    assertThat(
            jdbc.sql("select count(*) from order_lines where product_id = :id")
                .param("id", producto)
                .query(Integer.class)
                .single())
        .isZero();
  }

  private void respuesta(SobreMensaje<?> sobre) {
    inventario.enviar(
        Topicos.INVENTARIO_EVENTOS, sobre.idAgregado().toString(), json.writeValueAsString(sobre));
  }

  private ConsumerRecord<String, String> esperarMensajeMuerto(String clave) {
    return await()
        .atMost(TIMEOUT)
        .until(() -> mensajesMuertos.recibidoPara(clave), registros -> !registros.isEmpty())
        .getFirst();
  }

  private static String causaDe(ConsumerRecord<String, String> mensajeMuerto) {
    return new String(
        mensajeMuerto.headers().lastHeader("kafka_dlt-exception-cause-fqcn").value(),
        StandardCharsets.UTF_8);
  }
}
