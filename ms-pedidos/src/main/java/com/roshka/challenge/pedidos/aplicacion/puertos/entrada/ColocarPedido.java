package com.roshka.challenge.pedidos.aplicacion.puertos.entrada;

import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.util.List;

public interface ColocarPedido {

  /** Crea un pedido PENDING y registra ReservarStock en el outbox, en una sola transacción. */
  Pedido colocar(List<LineaPedido> lineas, String idCorrelacion);
}
