package com.roshka.challenge.pedidos.aplicacion.idempotencia;

import java.util.Objects;
import java.util.regex.Pattern;

/** Una solicitud de escritura identificada por su Idempotency-Key y el hash de lo que pide. */
public record SolicitudIdempotente(String clave, String huella) {

  public static final int LONGITUD_MAXIMA_CLAVE = 100;
  private static final Pattern FORMATO_CLAVE = Pattern.compile("[A-Za-z0-9_.:-]{1,100}");

  public SolicitudIdempotente {
    if (clave == null || clave.isBlank()) {
      throw new ClaveIdempotenciaAusente();
    }
    if (!FORMATO_CLAVE.matcher(clave).matches()) {
      throw new ClaveIdempotenciaInvalida();
    }
    Objects.requireNonNull(huella, "huella");
  }
}
