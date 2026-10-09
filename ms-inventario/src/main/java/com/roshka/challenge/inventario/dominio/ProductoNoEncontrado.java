package com.roshka.challenge.inventario.dominio;

import java.util.UUID;

public class ProductoNoEncontrado extends RuntimeException {

  public ProductoNoEncontrado(UUID idProducto) {
    super("El producto " + idProducto + " no existe");
  }
}
