package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roshka.challenge.inventario.dominio.AjusteStock;
import com.roshka.challenge.inventario.dominio.Producto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Stock disponible que debe quedar en el producto.")
record FijarStockRequest(
    @JsonProperty("available")
        @NotNull
        @Schema(
            description = "Unidades disponibles. Las reservadas no cambian.",
            example = "50",
            minimum = "0",
            maximum = "" + Producto.TOTAL_MAXIMO)
        Integer disponible,
    @JsonProperty("reason")
        @Schema(
            description = "Motivo del cambio (opcional).",
            example = "Reposición semanal",
            maxLength = AjusteStock.LONGITUD_MAXIMA_MOTIVO,
            nullable = true)
        String motivo) {}
