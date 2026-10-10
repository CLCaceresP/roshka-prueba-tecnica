package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roshka.challenge.inventario.dominio.Producto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Producto con su stock.")
record ProductoResponse(
    @JsonProperty("productId") @Schema(description = "Id del producto.") UUID idProducto,
    @JsonProperty("name") @Schema(example = "Teclado mecánico") String nombre,
    @JsonProperty("total") @Schema(description = "Stock total.", example = "25") int total,
    @JsonProperty("available") @Schema(description = "Unidades disponibles.", example = "20")
        int disponible,
    @JsonProperty("reserved") @Schema(description = "Unidades reservadas.", example = "5")
        int reservado) {

  static ProductoResponse desde(Producto producto) {
    return new ProductoResponse(
        producto.id(),
        producto.nombre(),
        producto.total(),
        producto.disponible(),
        producto.reservado());
  }
}
