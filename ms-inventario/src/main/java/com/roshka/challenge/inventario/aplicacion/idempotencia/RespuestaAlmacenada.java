package com.roshka.challenge.inventario.aplicacion.idempotencia;

import java.util.Map;

/** La respuesta exitosa registrada para una Idempotency-Key y reproducida para sus duplicados. */
public record RespuestaAlmacenada(int estado, Map<String, String> cabeceras, String cuerpo) {

  public RespuestaAlmacenada {
    cabeceras = Map.copyOf(cabeceras);
  }
}
