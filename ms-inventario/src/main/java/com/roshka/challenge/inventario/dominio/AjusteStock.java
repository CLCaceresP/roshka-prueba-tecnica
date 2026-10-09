package com.roshka.challenge.inventario.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Pedido de dejar un producto con {@code disponible} unidades disponibles.
 *
 * @param motivo texto libre normalizado; {@code null} si no se indicó
 */
public record AjusteStock(UUID idProducto, int disponible, String motivo) {

  public static final int LONGITUD_MAXIMA_MOTIVO = 200;

  public static AjusteStock de(UUID idProducto, int disponible, String motivo) {
    return new AjusteStock(idProducto, disponible, motivo);
  }

  public AjusteStock {
    Objects.requireNonNull(idProducto, "idProducto");
    if (disponible < 0 || disponible > Producto.TOTAL_MAXIMO) {
      throw new ProductoInvalido(
          "El stock disponible (available) debe estar entre 0 y " + Producto.TOTAL_MAXIMO);
    }
    motivo = Producto.normalizar(motivo, "El motivo (reason)");
    if (motivo != null && motivo.isEmpty()) {
      motivo = null;
    }
    if (motivo != null && motivo.length() > LONGITUD_MAXIMA_MOTIVO) {
      throw new ProductoInvalido(
          "El motivo (reason) no puede superar los " + LONGITUD_MAXIMA_MOTIVO + " caracteres");
    }
  }
}
