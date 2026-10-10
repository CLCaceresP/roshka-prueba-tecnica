package com.roshka.challenge.pedidos.aplicacion;

import java.util.UUID;

/** Nombre del bloqueo distribuido que serializa todo lo que modifica un pedido. */
public final class BloqueosPedido {

  private BloqueosPedido() {}

  public static String nombrePara(UUID idPedido) {
    return "order:" + idPedido;
  }
}
