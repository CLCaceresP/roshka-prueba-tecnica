package com.roshka.challenge.pedidos.adaptadores.entrada.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.contrato.MotivoRechazoStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.pedidos.ContenedoresCompartidos;
import com.roshka.challenge.pedidos.SondaKafka;
import com.roshka.challenge.pedidos.aplicacion.Respuestas;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.CancelarPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ColocarPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ExpirarSagasEstancadas;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ObtenerPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.MotivoRechazoPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@DirtiesContext
@ImportTestcontainers(ContenedoresCompartidos.class)
class PedidoMensajeriaIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired ColocarPedido colocarPedido;
  @Autowired CancelarPedido cancelarPedido;
  @Autowired ObtenerPedido obtenerPedido;
  @Autowired ExpirarSagasEstancadas expirarSagasEstancadas;
  @Autowired PedidoRepository pedidos;
  @Autowired TransactionTemplate tx;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  private SondaKafka inventario;

  @BeforeEach
  void abrirSonda() {
    inventario =
        new SondaKafka(
            ContenedoresCompartidos.KAFKA.getBootstrapServers(), Topicos.INVENTARIO_COMANDOS);
  }

  @AfterEach
  void cerrarSonda() {
    inventario.close();
  }

  @Test
  @DisplayName(
      "REQ-06 REQ-17 · 003-AC-07 placing an order publishes ReservarStock keyed by orderId")
  void debe_publicar_reservar_stock_con_clave_del_pedido_cuando_se_coloca_el_pedido() {
    var pedido = colocarPedido.colocar(List.of(new LineaPedido(UUID.randomUUID(), 2)), "corr-msg");

    var comando = json.readTree(esperarComandos(pedido.id(), 1).getFirst().value());

    assertThat(comando.get("tipo").asString()).isEqualTo("ReservarStock");
    assertThat(comando.get("idCorrelacion").asString()).isEqualTo("corr-msg");
    assertThat(comando.get("carga").get("lineas").get(0).get("cantidad").asInt()).isEqualTo(2);
  }

  @Test
  @DisplayName("REQ-07 · 003-AC-01 una respuesta StockReservado confirma el pedido")
  void debe_confirmar_el_pedido_cuando_llega_stock_reservado() {
    var pedido = colocado();

    respuesta(Respuestas.stockReservado(pedido.id()));

    esperarEstado(pedido.id(), EstadoPedido.CONFIRMED);
  }

  @Test
  @DisplayName("REQ-07 · 003-AC-02 una respuesta StockRechazado rechaza el pedido con su motivo")
  void debe_rechazar_el_pedido_con_motivo_cuando_llega_stock_rechazado() {
    var pedido = colocado();

    respuesta(Respuestas.stockRechazado(pedido.id(), MotivoRechazoStock.STOCK_INSUFICIENTE));

    esperarEstado(pedido.id(), EstadoPedido.REJECTED);
    assertThat(obtenerPedido.get(pedido.id()).motivoRechazo())
        .isEqualTo(MotivoRechazoPedido.INSUFFICIENT_STOCK);
  }

  @Test
  @DisplayName(
      "REQ-14 REQ-26 · 003-AC-05 la misma respuesta entregada tres veces se aplica una vez")
  void debe_aplicar_la_respuesta_una_vez_cuando_se_entrega_tres_veces() {
    var pedido = colocado();
    var reservado = Respuestas.stockReservado(pedido.id());

    respuesta(reservado);
    respuesta(reservado);
    respuesta(reservado);

    esperarEstado(pedido.id(), EstadoPedido.CONFIRMED);
    await()
        .during(Duration.ofSeconds(2))
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(copiasProcesadasDe(reservado.idMensaje())).isEqualTo(1));
    assertThat(versionDe(pedido.id())).isEqualTo(1);
  }

  @Test
  @DisplayName("REQ-27 · 004-AC-04 una reserva posterior a la cancelación se compensa")
  void debe_publicar_la_compensacion_cuando_la_reserva_llega_despues_de_la_cancelacion() {
    var pedido = colocado();
    cancelarPedido.cancelar(pedido.id(), "corr-cancel");

    respuesta(Respuestas.stockReservado(pedido.id()));

    var motivos =
        esperarComandos(pedido.id(), 3).stream()
            .map(registro -> json.readTree(registro.value()))
            .filter(comando -> comando.get("tipo").asString().equals("LiberarStock"))
            .map(comando -> comando.get("carga").get("motivo").asString())
            .toList();
    assertThat(motivos).containsExactly("CANCELADO_POR_CLIENTE", "COMPENSACION_RESERVA_TARDIA");
    assertThat(obtenerPedido.get(pedido.id()).estado()).isEqualTo(EstadoPedido.CANCELLED);
  }

  @Test
  @DisplayName("REQ-07 REQ-27 · 003-AC-10 una saga estancada agota el tiempo y libera su stock")
  void debe_rechazar_y_publicar_liberacion_cuando_la_saga_agota_el_tiempo() {
    var estancado =
        Pedido.colocar(
            UUID.randomUUID(),
            List.of(new LineaPedido(UUID.randomUUID(), 1)),
            "corr-stalled",
            Instant.now().minus(Duration.ofHours(1)));
    estancado.extraerEventos();
    tx.executeWithoutResult(s -> pedidos.insertar(estancado));

    assertThat(expirarSagasEstancadas.expirarEstancadas()).isPositive();

    assertThat(obtenerPedido.get(estancado.id()).motivoRechazo())
        .isEqualTo(MotivoRechazoPedido.TIMEOUT);
    var liberar = json.readTree(esperarComandos(estancado.id(), 1).getFirst().value());
    assertThat(liberar.get("carga").get("motivo").asString()).isEqualTo("TIMEOUT_SAGA");
    assertThat(liberar.get("idCorrelacion").asString()).isEqualTo("corr-stalled");
  }

  @Test
  @DisplayName("REQ-26 · 011-AC-08 una respuesta con campos nuevos confirma el pedido")
  void debe_confirmar_el_pedido_cuando_la_respuesta_trae_campos_que_el_consumidor_no_conoce() {
    var pedido = colocado();
    var sobre = Respuestas.stockReservado(pedido.id());

    inventario.enviar(
        Topicos.INVENTARIO_EVENTOS,
        pedido.id().toString(),
        json.writeValueAsString(sobre).replace("{", "{\"campoNuevo\":\"x\","));

    esperarEstado(pedido.id(), EstadoPedido.CONFIRMED);
  }

  private Pedido colocado() {
    var pedido = colocarPedido.colocar(List.of(new LineaPedido(UUID.randomUUID(), 1)), "corr-msg");
    esperarComandos(pedido.id(), 1);
    return pedido;
  }

  private void respuesta(SobreMensaje<?> sobre) {
    inventario.enviar(
        Topicos.INVENTARIO_EVENTOS, sobre.idAgregado().toString(), json.writeValueAsString(sobre));
  }

  private List<ConsumerRecord<String, String>> esperarComandos(UUID idPedido, int contar) {
    return await()
        .atMost(TIMEOUT)
        .until(
            () -> inventario.recibidoPara(idPedido.toString()),
            registros -> registros.size() >= contar);
  }

  private void esperarEstado(UUID idPedido, EstadoPedido estado) {
    await()
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(obtenerPedido.get(idPedido).estado()).isEqualTo(estado));
  }

  private int copiasProcesadasDe(UUID idMensaje) {
    return jdbc.sql("select count(*) from processed_messages where message_id = :id")
        .param("id", idMensaje)
        .query(Integer.class)
        .single();
  }

  private long versionDe(UUID idPedido) {
    return jdbc.sql("select version from orders where id = :id")
        .param("id", idPedido)
        .query(Long.class)
        .single();
  }
}
