package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roshka.challenge.inventario.dominio.Producto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos del producto a crear.")
record RegistrarProductoRequest(
    @JsonProperty("name")
        @NotBlank
        @Size(max = Producto.LONGITUD_MAXIMA_NOMBRE)
        @Schema(
            description = "Nombre del producto. No puede repetirse.",
            example = "Teclado mecánico",
            maxLength = Producto.LONGITUD_MAXIMA_NOMBRE)
        String nombre,
    @JsonProperty("initialQuantity")
        @NotNull
        @Min(0)
        @Max(Producto.CANTIDAD_INICIAL_MAXIMA)
        @Schema(
            description = "Stock inicial.",
            example = "25",
            minimum = "0",
            maximum = "" + Producto.CANTIDAD_INICIAL_MAXIMA)
        Integer cantidadInicial) {}
