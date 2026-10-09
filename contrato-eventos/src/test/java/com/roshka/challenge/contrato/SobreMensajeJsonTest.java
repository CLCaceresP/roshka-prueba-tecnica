package com.roshka.challenge.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SobreMensajeJsonTest {

  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  @DisplayName(
      "REQ-17 · 003-AC-06 el sobre sobrevive a la ida y vuelta por JSON con una carga tipada")
  void debe_preservar_todos_los_campos_cuando_el_sobre_hace_ida_y_vuelta_por_json() {
    var idPedido = UUID.randomUUID();
    var sobre =
        new SobreMensaje<>(
            UUID.randomUUID(),
            TiposMensaje.RESERVAR_STOCK,
            TiposMensaje.VERSION_ESQUEMA,
            Instant.parse("2026-10-09T12:00:00Z"),
            "corr-1",
            null,
            idPedido,
            new ReservarStock(idPedido, List.of(new LineaStock(UUID.randomUUID(), 3))));

    var restaurado =
        json.readValue(
            json.writeValueAsString(sobre), new TypeReference<SobreMensaje<ReservarStock>>() {});

    assertThat(restaurado).isEqualTo(sobre);
  }

  @Test
  @DisplayName("REQ-17 · los nombres de campo del contrato son estables en el cable")
  void debe_usar_los_nombres_de_campo_del_contrato_al_serializar() {
    var idPedido = UUID.randomUUID();
    var sobre =
        new SobreMensaje<>(
            UUID.randomUUID(),
            TiposMensaje.STOCK_RECHAZADO,
            TiposMensaje.VERSION_ESQUEMA,
            Instant.parse("2026-10-09T12:00:00Z"),
            "corr-1",
            UUID.randomUUID(),
            idPedido,
            new StockRechazado(idPedido, MotivoRechazoStock.STOCK_INSUFICIENTE, List.of()));

    JsonNode nodo = json.readTree(json.writeValueAsString(sobre));

    assertThat(nodo.propertyNames())
        .containsExactlyInAnyOrder(
            "idMensaje",
            "tipo",
            "versionEsquema",
            "ocurridoEn",
            "idCorrelacion",
            "idCausa",
            "idAgregado",
            "carga");
    assertThat(nodo.get("ocurridoEn").asString()).isEqualTo("2026-10-09T12:00:00Z");
    assertThat(nodo.get("carga").get("motivo").asString()).isEqualTo("STOCK_INSUFICIENTE");
  }

  @Test
  @DisplayName("REQ-26 · un mensaje sin campos obligatorios no se puede construir")
  void debe_rechazar_el_sobre_cuando_falta_el_id_del_agregado() {
    assertThatThrownBy(
            () ->
                new SobreMensaje<>(
                    UUID.randomUUID(),
                    TiposMensaje.LIBERAR_STOCK,
                    TiposMensaje.VERSION_ESQUEMA,
                    Instant.now(),
                    "corr-1",
                    null,
                    null,
                    new LiberarStock(UUID.randomUUID(), MotivoLiberacion.CANCELADO_POR_CLIENTE)))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("idAgregado");
  }
}
