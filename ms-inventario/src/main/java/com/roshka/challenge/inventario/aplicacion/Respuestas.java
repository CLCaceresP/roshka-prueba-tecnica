package com.roshka.challenge.inventario.aplicacion;

import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.TiposMensaje;
import java.time.Clock;
import java.util.UUID;

/**
 * Construye la respuesta a un comando: misma correlación y mismo agregado, causada por el comando.
 */
final class Respuestas {

  private Respuestas() {}

  static <P> SobreMensaje<P> responderA(
      SobreMensaje<?> comando, String tipo, P carga, Clock reloj) {
    return new SobreMensaje<>(
        UUID.randomUUID(),
        tipo,
        TiposMensaje.VERSION_ESQUEMA,
        reloj.instant(),
        comando.idCorrelacion(),
        comando.idMensaje(),
        comando.idAgregado(),
        carga);
  }
}
