package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roshka.challenge.inventario.aplicacion.consulta.Pagina;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

@Schema(description = "Página de resultados.")
record PaginaResponse<T>(
    @JsonProperty("items") List<T> items,
    @JsonProperty("page") @Schema(description = "Número de página.", example = "0") int pagina,
    @JsonProperty("size") @Schema(description = "Tamaño de página.", example = "20") int tamanio,
    @JsonProperty("totalElements") @Schema(description = "Total de elementos.", example = "45")
        long totalElementos,
    @JsonProperty("totalPages") @Schema(description = "Total de páginas.", example = "3")
        int totalPaginas) {

  static <D, T> PaginaResponse<T> desde(Pagina<D> pagina, Function<D, T> conversion) {
    return new PaginaResponse<>(
        pagina.items().stream().map(conversion).toList(),
        pagina.pagina(),
        pagina.tamanio(),
        pagina.totalElementos(),
        pagina.totalPaginas());
  }
}
