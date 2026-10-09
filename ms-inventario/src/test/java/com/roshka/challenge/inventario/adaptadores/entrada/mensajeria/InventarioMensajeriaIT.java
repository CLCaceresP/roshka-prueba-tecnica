package com.roshka.challenge.inventario.adaptadores.entrada.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.LineaStock;
import com.roshka.challenge.contrato.MotivoLiberacion;
import com.roshka.challenge.contrato.ReservarStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.inventario.ContenedoresCompartidos;
import com.roshka.challenge.inventario.NombresUnicos;
import com.roshka.challenge.inventario.SondaKafka;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.RegistrarProducto;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@DirtiesContext
@ImportTestcontainers(ContenedoresCompartidos.class)
class InventarioMensajeriaIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired RegistrarProducto registrarProducto;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;

  private SondaKafka sonda;
  private final UUID idPedido = UUID.randomUUID();

  @BeforeEach
  void abrirSonda() {
    sonda =
        new SondaKafka(
            ContenedoresCompartidos.KAFKA.getBootstrapServers(), Topicos.INVENTARIO_EVENTOS);
  }

  @AfterEach
  void cerrarSonda() {
    sonda.close();
  }

  @Test
  @DisplayName(
      "REQ-11 REQ-17 · 003-AC-06/07 un ReservarStock se responde una vez, con key idPedido")
  void debe_reservar_y_responder_con_correlacion_cuando_se_consume_reservar_stock() {
    var producto = registrarProducto.registrar(NombresUnicos.de("keyboard"), 5).id();
    var comando = reservar(producto, 3);

    enviar(comando);

    var respuesta = esperarRespuestas(1).getFirst();
    var sobre = json.readTree(respuesta.value());
    assertThat(respuesta.key()).isEqualTo(idPedido.toString());
    assertThat(sobre.get("tipo").asString()).isEqualTo(TiposMensaje.STOCK_RESERVADO);
    assertThat(sobre.get("idCausa").asString()).isEqualTo(comando.idMensaje().toString());
    assertThat(sobre.get("idCorrelacion").asString()).isEqualTo(comando.idCorrelacion());
    assertThat(stockDe(producto)).containsExactly(2, 3);
  }

  @Test
  @DisplayName(
      "REQ-14 REQ-26 · 003-AC-04 the same ReservarStock delivered three times has effect once")
  void debe_reservar_una_vez_y_responder_una_vez_cuando_el_comando_se_entrega_tres_veces() {
    var producto = registrarProducto.registrar(NombresUnicos.de("keyboard"), 5).id();
    var comando = reservar(producto, 3);

    enviar(comando);
    enviar(comando);
    enviar(comando);

    esperarRespuestas(1);
    await()
        .during(Duration.ofSeconds(2))
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(sonda.recibidoPara(idPedido.toString())).hasSize(1));
    assertThat(stockDe(producto)).containsExactly(2, 3);
    assertThat(conteoProcesadosDe(comando.idMensaje())).isEqualTo(1);
  }

  @Test
  @DisplayName("REQ-27 · 004-AC-05 LiberarStock antes de ReservarStock deja un tombstone")
  void debe_registrar_tombstone_y_rechazar_reservar_posterior_cuando_liberar_llega_primero() {
    var producto = registrarProducto.registrar(NombresUnicos.de("keyboard"), 5).id();

    enviar(liberar());
    esperarRespuestas(1);
    enviar(reservar(producto, 3));

    var respuestas = esperarRespuestas(2).stream().map(r -> json.readTree(r.value())).toList();
    assertThat(respuestas.get(0).get("carga").get("desenlace").asString())
        .isEqualTo("TOMBSTONE_REGISTRADO");
    assertThat(respuestas.get(1).get("tipo").asString()).isEqualTo(TiposMensaje.STOCK_RECHAZADO);
    assertThat(respuestas.get(1).get("carga").get("motivo").asString())
        .isEqualTo("RESERVA_LIBERADA");
    assertThat(stockDe(producto)).containsExactly(5, 0);
  }

  @Test
  @DisplayName(
      "REQ-12 REQ-15 · 004-AC-11 dos LiberarStock distintos devuelven el stock una sola vez")
  void debe_devolver_el_stock_una_vez_cuando_reservar_va_seguido_de_dos_liberaciones() {
    var producto = registrarProducto.registrar(NombresUnicos.de("keyboard"), 5).id();

    enviar(reservar(producto, 3));
    enviar(liberar());
    enviar(liberar());

    var desenlaces =
        esperarRespuestas(3).stream()
            .map(r -> json.readTree(r.value()))
            .filter(e -> e.get("tipo").asString().equals(TiposMensaje.STOCK_LIBERADO))
            .map(e -> e.get("carga").get("desenlace").asString())
            .toList();
    assertThat(desenlaces).containsExactly("DEVUELTO", "NADA_QUE_LIBERAR");
    assertThat(stockDe(producto)).containsExactly(5, 0);
  }

  @Test
  @DisplayName("REQ-06 · 003-AC-09 el outbox marca como publicada cada respuesta")
  void debe_marcar_publicadas_las_filas_del_outbox_cuando_las_respuestas_llegan_a_kafka() {
    var producto = registrarProducto.registrar(NombresUnicos.de("keyboard"), 5).id();

    enviar(reservar(producto, 1));
    esperarRespuestas(1);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.sql(
                                """
                                select count(*) from outbox
                                 where aggregate_id = :orderId and published_at is null
                                """)
                            .param("orderId", idPedido)
                            .query(Integer.class)
                            .single())
                    .isZero());
  }

  @Test
  @DisplayName("REQ-26 · 011-AC-08 un sobre con campos nuevos se procesa igual que sin ellos")
  void debe_reservar_cuando_el_sobre_trae_campos_que_el_consumidor_no_conoce() {
    var producto = registrarProducto.registrar(NombresUnicos.de("campo-nuevo"), 5).id();
    var valor = json.writeValueAsString(reservar(producto, 2));

    sonda.enviar(
        Topicos.INVENTARIO_COMANDOS,
        idPedido.toString(),
        valor.replace("{", "{\"campoNuevo\":\"x\","));

    var sobre = json.readTree(esperarRespuestas(1).getFirst().value());
    assertThat(sobre.get("tipo").asString()).isEqualTo(TiposMensaje.STOCK_RESERVADO);
    assertThat(stockDe(producto)).containsExactly(3, 2);
  }

  private SobreMensaje<ReservarStock> reservar(UUID idProducto, int cantidad) {
    return sobre(
        TiposMensaje.RESERVAR_STOCK,
        new ReservarStock(idPedido, List.of(new LineaStock(idProducto, cantidad))));
  }

  private SobreMensaje<LiberarStock> liberar() {
    return sobre(
        TiposMensaje.LIBERAR_STOCK,
        new LiberarStock(idPedido, MotivoLiberacion.CANCELADO_POR_CLIENTE));
  }

  private <P> SobreMensaje<P> sobre(String tipo, P carga) {
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

  private void enviar(SobreMensaje<?> sobre) {
    sonda.enviar(Topicos.INVENTARIO_COMANDOS, idPedido.toString(), json.writeValueAsString(sobre));
  }

  private List<ConsumerRecord<String, String>> esperarRespuestas(int contar) {
    return await()
        .atMost(TIMEOUT)
        .until(
            () -> sonda.recibidoPara(idPedido.toString()),
            respuestas -> respuestas.size() >= contar);
  }

  private List<Integer> stockDe(UUID idProducto) {
    return jdbc.sql("select available, reserved from products where id = :id")
        .param("id", idProducto)
        .query((rs, n) -> List.of(rs.getInt("available"), rs.getInt("reserved")))
        .single();
  }

  private int conteoProcesadosDe(UUID idMensaje) {
    return jdbc.sql("select count(*) from processed_messages where message_id = :id")
        .param("id", idMensaje)
        .query(Integer.class)
        .single();
  }
}
