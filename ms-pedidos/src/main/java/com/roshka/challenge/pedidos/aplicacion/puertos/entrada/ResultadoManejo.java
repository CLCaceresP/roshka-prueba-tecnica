package com.roshka.challenge.pedidos.aplicacion.puertos.entrada;

public enum ResultadoManejo {
  APLICADO,
  /** El idMensaje ya se procesó: sin efecto, el mensaje se confirma. */
  DUPLICADO
}
