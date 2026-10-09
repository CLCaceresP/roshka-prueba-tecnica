package com.roshka.challenge.inventario.aplicacion.puertos.entrada;

import com.roshka.challenge.contrato.ReservarStock;
import com.roshka.challenge.contrato.SobreMensaje;

public interface ManejarReservarStock {

  /**
   * Reserva todas las líneas o ninguna y registra la respuesta en el outbox, en una sola
   * transacción. Es idempotente por idMensaje y por el estado de la reserva del pedido (spec 004).
   */
  ResultadoManejo manejar(SobreMensaje<ReservarStock> comando);
}
