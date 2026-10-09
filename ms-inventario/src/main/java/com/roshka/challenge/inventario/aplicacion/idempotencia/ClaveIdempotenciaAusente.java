package com.roshka.challenge.inventario.aplicacion.idempotencia;

public class ClaveIdempotenciaAusente extends RuntimeException {

  public ClaveIdempotenciaAusente() {
    super("Las solicitudes de escritura requieren el header Idempotency-Key");
  }
}
