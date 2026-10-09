package com.roshka.challenge.inventario.dominio;

public class ProductoInvalido extends RuntimeException {

  public ProductoInvalido(String mensaje) {
    super(mensaje);
  }
}
