package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Forma del cuerpo de todo error de la API (RFC 9457) para el documento OpenAPI. */
@Schema(description = "Cuerpo de las respuestas de error.")
record ProblemaResponse(
    @JsonProperty("type") @Schema(example = "about:blank") String tipo,
    @JsonProperty("title") @Schema(example = "Not Found") String titulo,
    @JsonProperty("status") @Schema(example = "404") int estado,
    @JsonProperty("detail") @Schema(description = "Descripción del error.") String detalle,
    @JsonProperty("instance") @Schema(example = "/products/6805e54f-71b3-4e67-b3c0-b40ea638fac1")
        String instancia,
    @JsonProperty("code")
        @Schema(
            description = "Código del error, estable entre versiones.",
            example = "PRODUCT_NOT_FOUND")
        String codigo) {}
