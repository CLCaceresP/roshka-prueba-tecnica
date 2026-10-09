package com.roshka.challenge.inventario.dominio;

import java.util.UUID;

/** El ajuste dejaría el total del producto por encima del máximo permitido. */
public class LimiteStockExcedido extends RuntimeException {

  public LimiteStockExcedido(UUID idProducto, int reservado, int disponible) {
    super(
        "El producto %s tiene %d unidades reservadas; dejar %d disponibles superaría el máximo de %d"
            .formatted(idProducto, reservado, disponible, Producto.TOTAL_MAXIMO));
  }
}
