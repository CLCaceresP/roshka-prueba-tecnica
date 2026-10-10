package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roshka.challenge.inventario.dominio.Producto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Stock de un producto.")
record StockResponse(
    @JsonProperty("productId") UUID idProducto,
    @JsonProperty("total") @Schema(description = "Stock total.", example = "25") int total,
    @JsonProperty("available") @Schema(description = "Unidades disponibles.", example = "20")
        int disponible,
    @JsonProperty("reserved") @Schema(description = "Unidades reservadas.", example = "5")
        int reservado) {

  static StockResponse desde(Producto producto) {
    return new StockResponse(
        producto.id(), producto.total(), producto.disponible(), producto.reservado());
  }
}
