package com.roshka.challenge.pedidos.dominio;

import java.util.List;
import java.util.UUID;

/**
 * Eventos de dominio que registra {@link Pedido}; el orquestador de la saga los convierte en
 * comandos.
 */
public sealed interface EventoPedido {

  UUID idPedido();

  record PedidoColocado(UUID idPedido, List<LineaPedido> lineas) implements EventoPedido {}

  record PedidoConfirmado(UUID idPedido) implements EventoPedido {}

  record PedidoRechazado(UUID idPedido, MotivoRechazoPedido motivo) implements EventoPedido {}

  record PedidoCancelado(UUID idPedido) implements EventoPedido {}

  record LiberacionStockSolicitada(UUID idPedido, CausaLiberacion causa) implements EventoPedido {}
}
