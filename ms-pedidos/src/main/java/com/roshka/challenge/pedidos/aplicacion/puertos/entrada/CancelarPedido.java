package com.roshka.challenge.pedidos.aplicacion.puertos.entrada;

import com.roshka.challenge.pedidos.dominio.Pedido;
import java.util.UUID;

public interface CancelarPedido {

  /**
   * Cancela el pedido y solicita la liberación de su stock. Cancelar un pedido cancelado no cambia
   * nada. Quien llama retiene el bloqueo del pedido (ver {@link SolicitudesIdempotentes}).
   *
   * @throws com.roshka.challenge.pedidos.dominio.PedidoNoCancelable cuando el pedido fue rechazado
   */
  Pedido cancelar(UUID idPedido, String idCorrelacion);
}
