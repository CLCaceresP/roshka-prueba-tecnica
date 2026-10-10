package com.roshka.challenge.pedidos.aplicacion.puertos.salida;

import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.MotivoRechazoPedido;
import java.time.Duration;

/** Métricas de negocio de la saga; se registran solo si la transacción actual commitea. */
public interface MetricasPedidos {

  void pedidoColocado();

  void pedidoTerminado(EstadoPedido estado, MotivoRechazoPedido motivo, Duration duracionSaga);
}
