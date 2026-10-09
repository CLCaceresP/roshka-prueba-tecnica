package com.roshka.challenge.inventario.aplicacion.consulta;

import java.util.List;

/** Una página de resultados con el total de elementos que cumplen la consulta. */
public record Pagina<T>(List<T> items, int pagina, int tamanio, long totalElementos) {

  public Pagina {
    items = List.copyOf(items);
  }

  public int totalPaginas() {
    return (int) ((totalElementos + tamanio - 1) / tamanio);
  }
}
