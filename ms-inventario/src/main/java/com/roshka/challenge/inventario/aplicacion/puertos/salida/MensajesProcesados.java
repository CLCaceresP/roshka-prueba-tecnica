package com.roshka.challenge.inventario.aplicacion.puertos.salida;

import java.util.UUID;

public interface MensajesProcesados {

  /**
   * Registra el mensaje como procesado dentro de la transacción actual.
   *
   * @return {@code false} cuando ya se había procesado (una entrega duplicada)
   */
  boolean marcarProcesado(UUID idMensaje);
}
