package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Forma del cuerpo de todo error de la API (RFC 9457) para el documento OpenAPI. */
@Schema(description = "Error de la API según RFC 9457, con un código estable en `code`.")
record ProblemaResponse(
    @JsonProperty("type") @Schema(example = "about:blank") String tipo,
    @JsonProperty("title") @Schema(example = "Not Found") String titulo,
    @JsonProperty("status") @Schema(example = "404") int estado,
    @JsonProperty("detail") @Schema(description = "Explicación para personas, en español.")
        String detalle,
    @JsonProperty("instance") @Schema(example = "/orders/12670fca-2289-4691-96fe-491cba878632")
        String instancia,
    @JsonProperty("code")
        @Schema(
            description = "Código de error estable; es el que debe usar el cliente.",
            example = "ORDER_NOT_FOUND")
        String codigo) {}
