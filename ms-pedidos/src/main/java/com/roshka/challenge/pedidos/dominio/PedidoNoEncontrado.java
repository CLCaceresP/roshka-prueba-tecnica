package com.roshka.challenge.pedidos.dominio;

import java.util.UUID;

public class PedidoNoEncontrado extends RuntimeException {

  public PedidoNoEncontrado(UUID idPedido) {
    super("El pedido " + idPedido + " no existe");
  }
}
