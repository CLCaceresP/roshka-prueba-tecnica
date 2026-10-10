package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roshka.challenge.pedidos.dominio.Pedido;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Datos del pedido a crear.")
record ColocarPedidoRequest(
    @JsonProperty("lines")
        @NotNull
        @Size(min = 1, max = Pedido.MAXIMO_LINEAS)
        @Schema(description = "Productos pedidos. No puede repetirse un producto.")
        List<@Valid Linea> lineas) {

  record Linea(
      @JsonProperty("productId") @NotNull UUID idProducto,
      @JsonProperty("quantity")
          @NotNull
          @Min(1)
          @Max(Pedido.CANTIDAD_MAXIMA)
          @Schema(description = "Cantidad pedida.", example = "2")
          Integer cantidad) {}
}
