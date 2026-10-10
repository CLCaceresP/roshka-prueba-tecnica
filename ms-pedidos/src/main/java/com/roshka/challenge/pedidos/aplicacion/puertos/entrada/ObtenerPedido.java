package com.roshka.challenge.pedidos.aplicacion.puertos.entrada;

import com.roshka.challenge.pedidos.dominio.Pedido;
import java.util.UUID;

public interface ObtenerPedido {

  /**
   * @throws com.roshka.challenge.pedidos.dominio.PedidoNoEncontrado cuando el pedido no existe
   */
  Pedido get(UUID idPedido);
}
