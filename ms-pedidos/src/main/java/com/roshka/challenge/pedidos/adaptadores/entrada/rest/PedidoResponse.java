package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.MotivoRechazoPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Pedido con su estado y sus líneas.")
record PedidoResponse(
    @JsonProperty("orderId") @Schema(description = "Id del pedido.") UUID idPedido,
    @JsonProperty("status")
        @Schema(description = "PENDING, CONFIRMED, REJECTED o CANCELLED.", example = "PENDING")
        EstadoPedido estado,
    @JsonProperty("rejectionReason")
        @Schema(
            description = "Motivo, solo si el pedido fue rechazado.",
            nullable = true,
            example = "INSUFFICIENT_STOCK")
        MotivoRechazoPedido motivoRechazo,
    @JsonProperty("lines") List<Linea> lineas,
    @JsonProperty("createdAt") @Schema(description = "Fecha de creación.") Instant creadoEn,
    @JsonProperty("updatedAt") @Schema(description = "Fecha de la última actualización.")
        Instant actualizadoEn) {

  record Linea(
      @JsonProperty("productId") UUID idProducto, @JsonProperty("quantity") int cantidad) {}

  static PedidoResponse desde(Pedido pedido) {
    return new PedidoResponse(
        pedido.id(),
        pedido.estado(),
        pedido.motivoRechazo(),
        pedido.lineas().stream()
            .map(linea -> new Linea(linea.idProducto(), linea.cantidad()))
            .toList(),
        pedido.creadoEn(),
        pedido.actualizadoEn());
  }
}
