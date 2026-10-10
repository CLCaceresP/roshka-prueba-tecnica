package com.roshka.challenge.pedidos.dominio;

public class PedidoNoCancelable extends RuntimeException {

  public PedidoNoCancelable(EstadoPedido estado) {
    super("Un pedido en estado " + estado + " no se puede cancelar");
  }
}
