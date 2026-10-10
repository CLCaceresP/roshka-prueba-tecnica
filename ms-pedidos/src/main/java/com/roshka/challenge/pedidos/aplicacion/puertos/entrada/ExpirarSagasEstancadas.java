package com.roshka.challenge.pedidos.aplicacion.puertos.entrada;

public interface ExpirarSagasEstancadas {

  /**
   * Rechaza con TIMEOUT los pedidos que llevan PENDING más tiempo que el umbral.
   *
   * @return cuántos pedidos se expiraron
   */
  int expirarEstancadas();
}
