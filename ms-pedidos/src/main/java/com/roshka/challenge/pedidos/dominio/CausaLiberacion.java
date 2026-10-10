package com.roshka.challenge.pedidos.dominio;

public enum CausaLiberacion {
  CANCELADO_POR_CLIENTE,
  TIMEOUT_SAGA,
  COMPENSACION_RESERVA_TARDIA
}
