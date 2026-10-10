package com.roshka.challenge.pedidos.aplicacion.idempotencia;

public class ClaveIdempotenciaAusente extends RuntimeException {

  public ClaveIdempotenciaAusente() {
    super("Las solicitudes de escritura requieren el header Idempotency-Key");
  }
}
