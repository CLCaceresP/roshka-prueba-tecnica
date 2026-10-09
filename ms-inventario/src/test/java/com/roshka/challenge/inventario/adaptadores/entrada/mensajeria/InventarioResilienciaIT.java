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
import com.roshka.challenge.inventario.InyeccionFallos;
import com.roshka.challenge.inventario.NombresUnicos;
import com.roshka.challenge.inventario.SondaKafka;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.RegistrarProducto;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "mensajeria.reintentos.tiempo-maximo-transcurrido=1s")
@DirtiesContext
@Import(InyeccionFallos.class)
@ImportTestcontainers(ContenedoresCompartidos.class)
class InventarioResilienciaIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static final String DLT = Topicos.INVENTARIO_COMANDOS + Topicos.SUFIJO_MENSAJE_MUERTO;

  @Autowired InyeccionFallos.Fallos fallos;
  @Autowired RegistrarProducto registrarProducto;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;
  @Autowired MeterRegistry medidores;

  private SondaKafka respuestas;
  private SondaKafka mensajesMuertos;
  private final UUID idPedido = UUID.randomUUID();

  @BeforeEach
  void abrirSondas() {
    respuestas =
        new SondaKafka(
            ContenedoresCompartidos.KAFKA.getBootstrapServers(), Topicos.INVENTARIO_EVENTOS);
    mensajesMuertos = new SondaKafka(ContenedoresCompartidos.KAFKA.getBootstrapServers(), DLT);
  }

  @AfterEach
  void cerrarSondas() {
    respuestas.close();
    mensajesMuertos.close();
  }

  @Test
  @DisplayName("REQ-18 · 003-AC-12 un registro malformado va a la DLT de inmediato y se cuenta")
  void debe_enviar_a_dlt_el_registro_malformado_y_seguir_consumiendo() {
    var mensajesMuertosAntes =
        medidores.counter("messages.dlt", "topic", Topicos.INVENTARIO_COMANDOS).count();
    var claveVenenosa = UUID.randomUUID().toString();
    var producto = registrarProducto.registrar(NombresUnicos.de("dlt"), 5).id();

    respuestas.enviar(Topicos.INVENTARIO_COMANDOS, claveVenenosa, "this is not an envelope");
    respuestas.enviar(
        Topicos.INVENTARIO_COMANDOS, idPedido.toString(), aJson(reservar(producto, 1)));

    var muerto =
        await()
            .atMost(TIMEOUT)
            .until(() -> mensajesMuertos.recibidoPara(claveVenenosa), r -> !r.isEmpty());
    assertThat(cabecera(muerto.getFirst(), "kafka_dlt-exception-cause-fqcn"))
        .isEqualTo(MensajeMalformado.class.getName());
    assertThat(cabecera(muerto.getFirst(), "kafka_dlt-original-topic"))
        .isEqualTo(Topicos.INVENTARIO_COMANDOS);
    esperarRespuestas(1);
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        medidores
                            .counter("messages.dlt", "topic", Topicos.INVENTARIO_COMANDOS)
                            .count())
                    .isEqualTo(mensajesMuertosAntes + 1));
  }

  @Test
  @DisplayName("REQ-18 REQ-26 · 003 caso borde: una versionEsquema desconocida no se reintenta")
  void debe_enviar_a_dlt_la_version_de_esquema_desconocida() {
    var producto = registrarProducto.registrar(NombresUnicos.de("schema"), 5).id();
    var futuro =
        aJson(reservar(producto, 1)).replace("\"versionEsquema\":1", "\"versionEsquema\":99");

    respuestas.enviar(Topicos.INVENTARIO_COMANDOS, idPedido.toString(), futuro);

    await()
        .atMost(TIMEOUT)
        .until(() -> mensajesMuertos.recibidoPara(idPedido.toString()), r -> !r.isEmpty());
    assertThat(stockDe(producto)).containsExactly(5, 0);
  }

  @Test
  @DisplayName(
      "REQ-18 · 003-AC-11 un fallo transitorio se reintenta en el lugar, conservando el orden")
  void debe_reintentar_el_fallo_transitorio_antes_de_procesar_el_siguiente_mensaje_del_pedido() {
    var producto = registrarProducto.registrar(NombresUnicos.de("retry"), 5).id();
    var reservar = reservar(producto, 3);
    fallos.fallarAlMarcarProcesado(reservar.idMensaje(), 2);

    respuestas.enviar(Topicos.INVENTARIO_COMANDOS, idPedido.toString(), aJson(reservar));
    respuestas.enviar(Topicos.INVENTARIO_COMANDOS, idPedido.toString(), aJson(liberar()));

    var desenlace =
        json.readTree(esperarRespuestas(2).get(1).value()).get("carga").get("desenlace").asString();
    assertThat(desenlace).isEqualTo("DEVUELTO");
    assertThat(stockDe(producto)).containsExactly(5, 0);
  }

  @Test
  @DisplayName("REQ-26 · 003-AC-08 un fallo después del efecto y antes del commit no deja nada")
  void debe_aplicar_la_reserva_una_vez_cuando_el_primer_intento_falla_antes_del_commit() {
    var producto = registrarProducto.registrar(NombresUnicos.de("before-commit"), 5).id();
    fallos.fallarAlAgregarRespuestaPara(idPedido, 1);

    respuestas.enviar(
        Topicos.INVENTARIO_COMANDOS, idPedido.toString(), aJson(reservar(producto, 3)));

    esperarRespuestas(1);
    assertThat(stockDe(producto)).containsExactly(2, 3);
    await()
        .during(Duration.ofSeconds(2))
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(respuestas.recibidoPara(idPedido.toString())).hasSize(1));
  }

  private SobreMensaje<ReservarStock> reservar(UUID idProducto, int cantidad) {
    return sobre(
        TiposMensaje.RESERVAR_STOCK,
        new ReservarStock(idPedido, List.of(new LineaStock(idProducto, cantidad))));
  }

  @Test
  @DisplayName(
      "REQ-12 REQ-26 REQ-27 · 003-AC-16 replaying a dead-lettered release returns stock once")
  void
      debe_devolver_el_stock_una_vez_cuando_la_liberacion_fallida_se_reproduce_desde_mensajes_muertos() {
    var producto = registrarProducto.registrar(NombresUnicos.de("release-replay"), 5).id();
    respuestas.enviar(
        Topicos.INVENTARIO_COMANDOS, idPedido.toString(), aJson(reservar(producto, 3)));
    esperarRespuestas(1);
    var comando = liberar();
    fallos.fallarAlMarcarProcesado(comando.idMensaje(), Integer.MAX_VALUE);
    respuestas.enviar(Topicos.INVENTARIO_COMANDOS, idPedido.toString(), aJson(comando));

    var muerto =
        await()
            .atMost(TIMEOUT)
            .until(
                () -> mensajesMuertos.recibidoPara(idPedido.toString()),
                registros -> !registros.isEmpty())
            .getFirst();
    assertThat(stockDe(producto)).containsExactly(2, 3);
    assertThat(json.readTree(muerto.value()).get("idMensaje").asString())
        .isEqualTo(comando.idMensaje().toString());
    fallos.fallarAlMarcarProcesado(comando.idMensaje(), 0);

    respuestas.enviar(Topicos.INVENTARIO_COMANDOS, muerto.key(), muerto.value());
    respuestas.enviar(Topicos.INVENTARIO_COMANDOS, muerto.key(), muerto.value());
    esperarRespuestas(2);
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(stockDe(producto)).containsExactly(5, 0);
              assertThat(
                      jdbc.sql("select status from reservations where order_id = :id")
                          .param("id", idPedido)
                          .query(String.class)
                          .single())
                  .isEqualTo("RELEASED");
              assertThat(
                      jdbc.sql("select count(*) from outbox where payload ->> 'idCausa' = :id")
                          .param("id", comando.idMensaje().toString())
                          .query(Integer.class)
                          .single())
                  .isEqualTo(1);
              assertThat(
                      medidores
                          .counter("messages.duplicated", "type", TiposMensaje.LIBERAR_STOCK)
                          .count())
                  .isGreaterThanOrEqualTo(1);
            });
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

  private String aJson(SobreMensaje<?> sobre) {
    return json.writeValueAsString(sobre);
  }

  private List<ConsumerRecord<String, String>> esperarRespuestas(int contar) {
    return await()
        .atMost(TIMEOUT)
        .until(() -> respuestas.recibidoPara(idPedido.toString()), r -> r.size() >= contar);
  }

  private static String cabecera(ConsumerRecord<String, String> registro, String nombre) {
    return new String(registro.headers().lastHeader(nombre).value(), StandardCharsets.UTF_8);
  }

  private List<Integer> stockDe(UUID idProducto) {
    return jdbc.sql("select available, reserved from products where id = :id")
        .param("id", idProducto)
        .query((rs, n) -> List.of(rs.getInt("available"), rs.getInt("reserved")))
        .single();
  }
}
