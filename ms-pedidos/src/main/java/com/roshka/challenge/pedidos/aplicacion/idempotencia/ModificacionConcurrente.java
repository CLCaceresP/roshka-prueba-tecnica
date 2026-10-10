package com.roshka.challenge.pedidos.aplicacion.idempotencia;

import java.time.Duration;

/**
 * Otra solicitud o paso de la saga está modificando el recurso; reintentar más tarde tendrá éxito.
 */
public class ModificacionConcurrente extends RuntimeException {

  private final Duration reintentarDespues;

  public ModificacionConcurrente(Duration reintentarDespues) {
    super("El pedido se está modificando concurrentemente");
    this.reintentarDespues = reintentarDespues;
  }

  public Duration reintentarDespues() {
    return reintentarDespues;
  }
}
