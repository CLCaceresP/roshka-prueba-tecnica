package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.AjustarStock;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Stock del producto después del cambio y el movimiento registrado.")
record StockActualizadoResponse(
    @JsonProperty("productId") @Schema(description = "Id del producto.") UUID idProducto,
    @JsonProperty("total") @Schema(description = "Stock total.", example = "57") int total,
    @JsonProperty("available") @Schema(description = "Unidades disponibles.", example = "50")
        int disponible,
    @JsonProperty("reserved") @Schema(description = "Unidades reservadas.", example = "7")
        int reservado,
    @JsonProperty("movement")
        @Schema(
            description = "Movimiento registrado; null si el stock ya tenía ese valor.",
            nullable = true)
        MovimientoResponse movimiento) {

  static StockActualizadoResponse desde(AjustarStock.Resultado resultado) {
    var producto = resultado.producto();
    return new StockActualizadoResponse(
        producto.id(),
        producto.total(),
        producto.disponible(),
        producto.reservado(),
        resultado.movimiento() == null ? null : MovimientoResponse.desde(resultado.movimiento()));
  }
}
