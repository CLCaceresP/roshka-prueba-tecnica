package com.roshka.challenge.pedidos.aplicacion;

import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ColocarPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.aplicacion.saga.SagaReserva;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ColocacionPedido implements ColocarPedido {

  private final PedidoRepository pedidos;
  private final SagaReserva saga;
  private final Clock reloj;

  public ColocacionPedido(PedidoRepository pedidos, SagaReserva saga, Clock reloj) {
    this.pedidos = pedidos;
    this.saga = saga;
    this.reloj = reloj;
  }

  @Override
  @Transactional
  public Pedido colocar(List<LineaPedido> lineas, String idCorrelacion) {
    var idPedido = UUID.randomUUID();
    var pasos = Pasos.proceso(ColocacionPedido.class, "Colocar pedido").con("orderId", idPedido);
    return pasos.ejecutar(
        () -> {
          var pedido =
              pasos.medir(
                  "Pedido creado y validado",
                  () -> Pedido.colocar(idPedido, lineas, idCorrelacion, reloj.instant()));
          pasos.medir("Pedido guardado en la base de datos", () -> pedidos.insertar(pedido));
          pasos.medir(
              "Saga notificada del pedido colocado",
              () -> saga.despachar(pedido, pedido.extraerEventos(), idCorrelacion, null));
          return pedido;
        });
  }
}
