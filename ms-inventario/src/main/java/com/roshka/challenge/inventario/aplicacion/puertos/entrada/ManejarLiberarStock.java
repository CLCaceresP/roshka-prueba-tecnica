package com.roshka.challenge.inventario.aplicacion.puertos.entrada;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.SobreMensaje;

public interface ManejarLiberarStock {

  /**
   * Devuelve el stock reservado como máximo una vez, o registra un tombstone cuando todavía no
   * había nada reservado, y registra la respuesta en el outbox, en una sola transacción.
   */
  ResultadoManejo manejar(SobreMensaje<LiberarStock> comando);
}
