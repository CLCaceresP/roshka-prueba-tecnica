package com.roshka.challenge.inventario.aplicacion.idempotencia;

import java.time.Duration;

public class ClaveIdempotenciaEnProceso extends RuntimeException {

  private final Duration reintentarDespues;

  public ClaveIdempotenciaEnProceso(Duration reintentarDespues) {
    super("Una solicitud con la misma Idempotency-Key está en proceso");
    this.reintentarDespues = reintentarDespues;
  }

  public Duration reintentarDespues() {
    return reintentarDespues;
  }
}
