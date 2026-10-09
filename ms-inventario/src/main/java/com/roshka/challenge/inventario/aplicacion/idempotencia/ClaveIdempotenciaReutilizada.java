package com.roshka.challenge.inventario.aplicacion.idempotencia;

public class ClaveIdempotenciaReutilizada extends RuntimeException {

  public ClaveIdempotenciaReutilizada() {
    super("La Idempotency-Key ya se usó en una solicitud distinta");
  }
}
