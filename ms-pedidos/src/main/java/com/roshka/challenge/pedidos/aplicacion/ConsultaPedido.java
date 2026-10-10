package com.roshka.challenge.pedidos.aplicacion;

import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ObtenerPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.dominio.Pedido;
import com.roshka.challenge.pedidos.dominio.PedidoNoEncontrado;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaPedido implements ObtenerPedido {

  private static final Logger log = LoggerFactory.getLogger(ConsultaPedido.class);

  private final PedidoRepository pedidos;

  public ConsultaPedido(PedidoRepository pedidos) {
    this.pedidos = pedidos;
  }

  @Override
  @Transactional(readOnly = true)
  public Pedido get(UUID idPedido) {
    var pedido = pedidos.buscarPorId(idPedido).orElseThrow(() -> new PedidoNoEncontrado(idPedido));
    var unidades = LineasLegibles.unidades(pedido.lineas());
    AtributosLog.conPedido(log.atInfo(), idPedido)
        .addKeyValue("status", pedido.estado().name())
        .addKeyValue("lines", pedido.lineas().size())
        .addKeyValue("units", unidades)
        .log(
            "Pedido %s consultado: estado %s, %d líneas, %d unidades (%s)"
                .formatted(
                    idPedido,
                    pedido.estado(),
                    pedido.lineas().size(),
                    unidades,
                    LineasLegibles.descripcion(pedido.lineas())));
    return pedido;
  }
}
