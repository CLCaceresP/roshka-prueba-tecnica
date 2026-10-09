package com.roshka.challenge.inventario.aplicacion.puertos.salida;

public class BloqueoNoAdquirido extends RuntimeException {

  public BloqueoNoAdquirido(String nombreBloqueo) {
    super("El bloqueo " + nombreBloqueo + " lo retiene otra solicitud");
  }
}
