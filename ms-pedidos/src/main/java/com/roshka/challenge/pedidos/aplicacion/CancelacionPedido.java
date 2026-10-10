package com.roshka.challenge.pedidos.aplicacion;

import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.CancelarPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.aplicacion.saga.SagaReserva;
import com.roshka.challenge.pedidos.dominio.Pedido;
import com.roshka.challenge.pedidos.dominio.PedidoNoEncontrado;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelacionPedido implements CancelarPedido {

  private static final Logger log = LoggerFactory.getLogger(CancelacionPedido.class);

  private final PedidoRepository pedidos;
  private final SagaReserva saga;
  private final Clock reloj;

  public CancelacionPedido(PedidoRepository pedidos, SagaReserva saga, Clock reloj) {
    this.pedidos = pedidos;
    this.saga = saga;
    this.reloj = reloj;
  }

  @Override
  @Transactional
  public Pedido cancelar(UUID idPedido, String idCorrelacion) {
    var pasos = Pasos.proceso(CancelacionPedido.class, "Cancelar pedido").con("orderId", idPedido);
    return pasos.ejecutar(
        () -> {
          var pedido =
              pasos.medir(
                  "Pedido cargado",
                  () ->
                      pedidos
                          .buscarPorId(idPedido)
                          .orElseThrow(() -> new PedidoNoEncontrado(idPedido)));
          var eventos =
              pasos.medir(
                  "Cancelación aplicada",
                  () -> {
                    pedido.cancelar(reloj.instant());
                    return pedido.extraerEventos();
                  });
          if (eventos.isEmpty()) {
            log.atInfo()
                .addKeyValue("orderId", idPedido.toString())
                .addKeyValue("status", pedido.estado().name())
                .log(
                    "El pedido %s ya estaba %s: no hay nada que cancelar"
                        .formatted(idPedido, pedido.estado()));
            return pedido;
          }
          pasos.medir(
              "Pedido actualizado y comandos registrados",
              () -> {
                saga.despachar(pedido, eventos, idCorrelacion, null);
                pedidos.actualizar(pedido);
              });
          return pedido;
        });
  }
}
