package com.roshka.challenge.inventario.aplicacion.puertos.salida;

import com.roshka.challenge.inventario.dominio.MotivoRechazo;

/** Métricas de negocio de las reservas; se registran solo si la transacción actual commitea. */
public interface MetricasInventario {

  /**
   * @param rechazo {@code null} cuando la reserva tuvo éxito
   */
  void reservaDecidida(MotivoRechazo rechazo);
}
