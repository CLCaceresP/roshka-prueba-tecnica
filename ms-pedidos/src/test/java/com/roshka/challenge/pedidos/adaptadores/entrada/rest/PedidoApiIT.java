package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.contrato.MotivoRechazoStock;
import com.roshka.challenge.pedidos.ContenedoresCompartidos;
import com.roshka.challenge.pedidos.aplicacion.BloqueosPedido;
import com.roshka.challenge.pedidos.aplicacion.Respuestas;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ManejarRespuestaInventario;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@ImportTestcontainers(ContenedoresCompartidos.class)
class PedidoApiIT {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired RedissonClient redisson;
  @Autowired ManejarRespuestaInventario respuestasInventario;

  private final UUID producto = UUID.randomUUID();

  @Test
  @DisplayName("REQ-03 REQ-19 · 002-AC-01 colocar un pedido devuelve 202, Location y PENDING")
  void debe_aceptar_pedido_como_pendiente_cuando_la_solicitud_es_valida() {
    var resultado = colocar(claveNueva(), lineasDe(producto, 2));

    assertThat(resultado).hasStatus(HttpStatus.ACCEPTED);
    var idPedido = idDesde(resultado);
    assertThat(resultado).headers().hasValue("Location", "/orders/" + idPedido);
    assertThat(resultado).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
    assertThat(resultado).bodyJson().extractingPath("$.lines[0].quantity").isEqualTo(2);
  }

  @Test
  @DisplayName("REQ-06 · 002-AC-02 el comando ReservarStock queda en el outbox junto con el pedido")
  void debe_registrar_reservar_stock_en_el_outbox_cuando_se_coloca_el_pedido() {
    var idPedido = idDesde(colocar(claveNueva(), lineasDe(producto, 2)));

    assertThat(tiposOutboxPara(idPedido)).containsExactly("ReservarStock");
  }

  @Test
  @DisplayName("REQ-04 REQ-20 · 002-AC-04/05 un pedido se puede consultar; uno desconocido es 404")
  void debe_devolver_el_pedido_al_consultar_y_404_cuando_es_desconocido() {
    var idPedido = idDesde(colocar(claveNueva(), lineasDe(producto, 2)));

    assertThat(mvc.get().uri("/orders/{id}", idPedido))
        .bodyJson()
        .extractingPath("$.orderId")
        .isEqualTo(idPedido.toString());
    var ausente = mvc.get().uri("/orders/{id}", UUID.randomUUID()).exchange();
    assertThat(ausente).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(ausente).bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_FOUND");
  }

  @Test
  @DisplayName("REQ-19 · 002-AC-06 los pedidos inválidos son 400 y no crean nada")
  void debe_rechazar_pedidos_invalidos_sin_efectos_secundarios() {
    var vacio = colocar(claveNueva(), "{\"lines\":[]}");
    var cero = colocar(claveNueva(), lineasDe(producto, 0));
    var repetido =
        colocar(
            claveNueva(),
            "{\"lines\":[{\"productId\":\"%s\",\"quantity\":1},{\"productId\":\"%s\",\"quantity\":2}]}"
                .formatted(producto, producto));

    assertThat(vacio).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(vacio).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(cero).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(repetido).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(repetido).bodyJson().extractingPath("$.code").isEqualTo("DUPLICATE_PRODUCT_LINE");
    assertThat(pedidosConProducto(producto)).isZero();
  }

  @Test
  @DisplayName("REQ-09 REQ-24 · 005-AC-02 repetir POST /orders reproduce el mismo pedido")
  void debe_reproducir_el_mismo_pedido_cuando_la_solicitud_se_repite() throws Exception {
    var clave = claveNueva();

    var primero = colocar(clave, lineasDe(producto, 2));
    var segundo = colocar(clave, lineasDe(producto, 2));

    assertThat(segundo).hasStatus(HttpStatus.ACCEPTED);
    assertThat(segundo).headers().hasValue("Idempotent-Replayed", "true");
    assertThat(segundo.getResponse().getContentAsString())
        .isEqualTo(primero.getResponse().getContentAsString());
    assertThat(segundo).headers().hasValue("Location", primero.getResponse().getHeader("Location"));
    assertThat(pedidosConProducto(producto)).isEqualTo(1);
    assertThat(tiposOutboxPara(idDesde(primero))).containsExactly("ReservarStock");
  }

  @Test
  @DisplayName(
      "REQ-24 · 005-AC-01/03/04 clave ausente 400, clave reutilizada 422, también en otra ruta")
  void debe_aplicar_las_reglas_de_la_clave_de_idempotencia() {
    var ausente =
        mvc.post()
            .uri("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(lineasDe(producto, 1))
            .exchange();
    var clave = claveNueva();
    var idPedido = idDesde(colocar(clave, lineasDe(producto, 1)));
    var otroCuerpo = colocar(clave, lineasDe(producto, 3));
    var otraRuta = cancelar(clave, idPedido);

    assertThat(ausente).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(ausente).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_MISSING");
    assertThat(otroCuerpo).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(otraRuta).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(otraRuta).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
  }

  @Test
  @DisplayName("REQ-05 REQ-21 · 004 RN-02 cancelar un pedido pendiente devuelve CANCELLED")
  void debe_cancelar_pedido_pendiente_y_registrar_liberar_stock() {
    var idPedido = idDesde(colocar(claveNueva(), lineasDe(producto, 2)));

    var resultado = cancelar(claveNueva(), idPedido);

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
    assertThat(tiposOutboxPara(idPedido)).containsExactly("ReservarStock", "LiberarStock");
  }

  @Test
  @DisplayName("REQ-09 · 004-AC-08 cancelar de nuevo con otra clave es 200 y no emite nada")
  void debe_responder_ok_sin_comandos_nuevos_cuando_el_pedido_ya_esta_cancelado() {
    var idPedido = idDesde(colocar(claveNueva(), lineasDe(producto, 2)));
    cancelar(claveNueva(), idPedido);

    var otraVez = cancelar(claveNueva(), idPedido);

    assertThat(otraVez).hasStatus(HttpStatus.OK);
    assertThat(otraVez).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
    assertThat(tiposOutboxPara(idPedido)).containsExactly("ReservarStock", "LiberarStock");
  }

  @Test
  @DisplayName("REQ-21 · 004-AC-09 un pedido rechazado es 409 ORDER_NOT_CANCELLABLE")
  void debe_negar_la_cancelacion_cuando_el_pedido_fue_rechazado() {
    var idPedido = idDesde(colocar(claveNueva(), lineasDe(producto, 2)));
    respuestasInventario.alStockRechazado(
        Respuestas.stockRechazado(idPedido, MotivoRechazoStock.STOCK_INSUFICIENTE));

    var resultado = cancelar(claveNueva(), idPedido);

    assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_CANCELLABLE");
  }

  @Test
  @DisplayName("REQ-21 · cancelar un pedido desconocido es 404")
  void debe_responder_no_encontrado_cuando_se_cancela_un_pedido_desconocido() {
    var resultado = cancelar(claveNueva(), UUID.randomUUID());

    assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_FOUND");
  }

  @Test
  @DisplayName(
      "REQ-25 REQ-27 · 004 RN-05 un pedido bloqueado por un paso de la saga es 409 con Retry-After")
  void debe_responder_modificacion_concurrente_cuando_el_bloqueo_del_pedido_esta_retenido()
      throws Exception {
    var idPedido = idDesde(colocar(claveNueva(), lineasDe(producto, 2)));
    var bloqueo = redisson.getLock(BloqueosPedido.nombrePara(idPedido));
    bloqueo.lockAsync(30, TimeUnit.SECONDS, 4242L).get(5, TimeUnit.SECONDS);
    try {
      var resultado = cancelar(claveNueva(), idPedido);

      assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
      assertThat(resultado).headers().hasValue("Retry-After", "1");
      assertThat(resultado)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo("CONCURRENT_MODIFICATION");
    } finally {
      bloqueo.forceUnlock();
    }
    assertThat(mvc.get().uri("/orders/{id}", idPedido))
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("PENDING");
  }

  @Test
  @DisplayName("BON-03 · 006-AC-04 el id de correlación se conserva o se genera y llega a la saga")
  void debe_propagar_el_id_de_correlacion_de_la_solicitud_al_outbox() {
    var resultado =
        mvc.post()
            .uri("/orders")
            .header("Idempotency-Key", claveNueva())
            .header("X-Correlation-Id", "corr-from-client")
            .contentType(MediaType.APPLICATION_JSON)
            .content(lineasDe(producto, 1))
            .exchange();
    var generado = mvc.get().uri("/orders/{id}", UUID.randomUUID()).exchange();

    assertThat(resultado).headers().hasValue("X-Correlation-Id", "corr-from-client");
    assertThat(correlacionDelComandoPara(idDesde(resultado))).isEqualTo("corr-from-client");
    assertThat(generado.getResponse().getHeader("X-Correlation-Id")).isNotBlank();
  }

  @Test
  @DisplayName(
      "REQ-24 · 005-AC-08 un intento fallido no se registra: la clave funciona una vez corregida")
  void debe_aceptar_pedido_corregido_cuando_el_intento_previo_con_la_misma_clave_fallo() {
    var clave = claveNueva();

    var fallido = colocar(clave, lineasDe(producto, 0));
    var fijo = colocar(clave, lineasDe(producto, 2));

    assertThat(fallido).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(fijo).hasStatus(HttpStatus.ACCEPTED);
    assertThat(fijo.getResponse().getHeader("Idempotent-Replayed")).isNull();
    assertThat(pedidosConProducto(producto)).isEqualTo(1);
  }

  @Test
  @DisplayName("REQ-05 REQ-12 · 004-AC-03 cancelar un pedido confirmado libera su stock")
  void debe_registrar_liberar_stock_cuando_se_cancela_un_pedido_confirmado() {
    var idPedido = idDesde(colocar(claveNueva(), lineasDe(producto, 2)));
    respuestasInventario.alStockReservado(Respuestas.stockReservado(idPedido));

    var resultado = cancelar(claveNueva(), idPedido);

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
    assertThat(tiposOutboxPara(idPedido)).containsExactly("ReservarStock", "LiberarStock");
  }

  @Test
  @DisplayName(
      "REQ-24 · 005-AC-01 a cancellation without Idempotency-Key is 400 and changes nothing")
  void debe_rechazar_la_cancelacion_cuando_falta_la_clave_de_idempotencia() {
    var idPedido = idDesde(colocar(claveNueva(), lineasDe(producto, 1)));

    var resultado = mvc.delete().uri("/orders/{id}", idPedido).exchange();

    assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_MISSING");
    assertThat(tiposOutboxPara(idPedido)).containsExactly("ReservarStock");
  }

  @Test
  @DisplayName("REQ-09 REQ-24 · 005-AC-11 repetir una cancelación con su clave la reproduce")
  void debe_reproducir_la_cancelacion_cuando_se_repite_con_la_misma_clave() throws Exception {
    var idPedido = idDesde(colocar(claveNueva(), lineasDe(producto, 1)));
    var clave = claveNueva();

    var primero = cancelar(clave, idPedido);
    var segundo = cancelar(clave, idPedido);

    assertThat(segundo).hasStatus(HttpStatus.OK);
    assertThat(segundo).headers().hasValue("Idempotent-Replayed", "true");
    assertThat(segundo.getResponse().getContentAsString())
        .isEqualTo(primero.getResponse().getContentAsString());
    assertThat(tiposOutboxPara(idPedido)).containsExactly("ReservarStock", "LiberarStock");
  }

  @Test
  @DisplayName("REQ-19 · 011-AC-07 un orderId propio o una propiedad extra en una línea es 400")
  void debe_rechazar_el_pedido_cuando_trae_propiedades_desconocidas() {
    var conIdPropio =
        colocar(
            claveNueva(),
            "{\"orderId\":\"%s\",\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}"
                .formatted(UUID.randomUUID(), producto));
    var lineaConExtra =
        colocar(
            claveNueva(),
            "{\"lines\":[{\"productId\":\"%s\",\"quantity\":1,\"price\":9}]}".formatted(producto));

    assertThat(conIdPropio).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(conIdPropio).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(conIdPropio)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .contains("Propiedad desconocida «orderId»")
        .contains("lines");
    assertThat(lineaConExtra).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(lineaConExtra)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .contains("Propiedad desconocida «price»");
    assertThat(pedidosConProducto(producto)).isZero();
  }

  @Test
  @DisplayName("REQ-19 · 013 los errores de validación nombran los campos del contrato en inglés")
  void debe_nombrar_los_campos_del_contrato_cuando_el_pedido_es_invalido() {
    var sinLineas = colocar(claveNueva(), "{\"lines\":[]}");
    var cantidadCero = colocar(claveNueva(), lineasDe(producto, 0));

    assertThat(sinLineas)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .startsWith("lines ")
        .doesNotContain("lineas");
    assertThat(cantidadCero)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .startsWith("lines[0].quantity ")
        .doesNotContain("cantidad");
  }

  private MvcTestResult colocar(String clave, String cuerpo) {
    return mvc.post()
        .uri("/orders")
        .header("Idempotency-Key", clave)
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo)
        .exchange();
  }

  private MvcTestResult cancelar(String clave, UUID idPedido) {
    return mvc.delete().uri("/orders/{id}", idPedido).header("Idempotency-Key", clave).exchange();
  }

  private static String lineasDe(UUID idProducto, int cantidad) {
    return "{\"lines\":[{\"productId\":\"%s\",\"quantity\":%d}]}".formatted(idProducto, cantidad);
  }

  private static UUID idDesde(MvcTestResult resultado) {
    var ubicacion = resultado.getResponse().getHeader("Location");
    return UUID.fromString(ubicacion.substring(ubicacion.lastIndexOf('/') + 1));
  }

  private List<String> tiposOutboxPara(UUID idPedido) {
    return jdbc.sql("select type from outbox where aggregate_id = :id order by id")
        .param("id", idPedido)
        .query(String.class)
        .list();
  }

  private String correlacionDelComandoPara(UUID idPedido) {
    return jdbc.sql(
            "select payload ->> 'idCorrelacion' from outbox where aggregate_id = :id order by id")
        .param("id", idPedido)
        .query(String.class)
        .list()
        .getFirst();
  }

  private int pedidosConProducto(UUID idProducto) {
    return jdbc.sql("select count(*) from order_lines where product_id = :id")
        .param("id", idProducto)
        .query(Integer.class)
        .single();
  }

  @Test
  @DisplayName("REQ-28 · los endpoints están documentados con OpenAPI")
  void debe_publicar_el_documento_openapi_con_todos_los_endpoints() {
    var documento = mvc.get().uri("/v3/api-docs").exchange();

    assertThat(documento).hasStatus(HttpStatus.OK);
    assertThat(documento)
        .bodyJson()
        .extractingPath("$.paths['/orders/{orderId}'].delete")
        .isNotNull();
  }

  private static String claveNueva() {
    return UUID.randomUUID().toString();
  }
}
