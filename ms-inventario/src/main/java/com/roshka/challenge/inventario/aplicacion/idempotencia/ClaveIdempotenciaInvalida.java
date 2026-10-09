package com.roshka.challenge.inventario.aplicacion.idempotencia;

public class ClaveIdempotenciaInvalida extends RuntimeException {

  public ClaveIdempotenciaInvalida() {
    super(
        "Idempotency-Key debe tener de 1 a "
            + SolicitudIdempotente.LONGITUD_MAXIMA_CLAVE
            + " caracteres entre A-Z a-z 0-9 _ . : -");
  }
}
