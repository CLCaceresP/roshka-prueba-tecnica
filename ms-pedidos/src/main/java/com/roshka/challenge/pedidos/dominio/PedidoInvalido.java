package com.roshka.challenge.pedidos.dominio;

public class PedidoInvalido extends RuntimeException {

  public PedidoInvalido(String mensaje) {
    super(mensaje);
  }
}
