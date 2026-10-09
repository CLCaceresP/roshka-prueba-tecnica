package com.roshka.challenge.inventario.aplicacion.puertos.salida;

public class BloqueoNoDisponible extends RuntimeException {

  public BloqueoNoDisponible(String nombreBloqueo, Throwable causa) {
    super("Servicio de bloqueo no disponible al adquirir " + nombreBloqueo, causa);
  }
}
