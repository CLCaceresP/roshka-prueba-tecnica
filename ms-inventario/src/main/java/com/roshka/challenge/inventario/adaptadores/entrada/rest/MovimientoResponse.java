package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roshka.challenge.inventario.dominio.MovimientoStock;
import com.roshka.challenge.inventario.dominio.TipoAjuste;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Ajuste de stock registrado.")
record MovimientoResponse(
    @JsonProperty("movementId") UUID idMovimiento,
    @JsonProperty("type") @Schema(example = "REMOVE") TipoAjuste tipo,
    @JsonProperty("quantity") int cantidad,
    @JsonProperty("reason") @Schema(example = "Merma", nullable = true) String motivo,
    @JsonProperty("createdAt") Instant creadoEn,
    @JsonProperty("totalAfter") int totalDespues,
    @JsonProperty("availableAfter") int disponibleDespues) {

  static MovimientoResponse desde(MovimientoStock movimiento) {
    return new MovimientoResponse(
        movimiento.id(),
        movimiento.tipo(),
        movimiento.cantidad(),
        movimiento.motivo(),
        movimiento.creadoEn(),
        movimiento.totalDespues(),
        movimiento.disponibleDespues());
  }
}
