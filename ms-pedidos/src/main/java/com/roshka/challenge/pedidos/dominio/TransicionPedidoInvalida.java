package com.roshka.challenge.pedidos.dominio;

/** Una entrada que la máquina de estados declara imposible en el estado actual (spec 004). */
public class TransicionPedidoInvalida extends RuntimeException {

  public TransicionPedidoInvalida(EstadoPedido estado, String entrada) {
    super(entrada + " no es posible para un pedido en estado " + estado);
  }
}
