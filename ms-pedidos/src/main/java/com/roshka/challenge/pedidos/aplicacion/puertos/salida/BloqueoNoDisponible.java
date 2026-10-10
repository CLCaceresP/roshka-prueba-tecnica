package com.roshka.challenge.pedidos.aplicacion.puertos.salida;

public class BloqueoNoDisponible extends RuntimeException {

  public BloqueoNoDisponible(String nombreBloqueo, Throwable causa) {
    super("Servicio de bloqueo no disponible al adquirir " + nombreBloqueo, causa);
  }
}
