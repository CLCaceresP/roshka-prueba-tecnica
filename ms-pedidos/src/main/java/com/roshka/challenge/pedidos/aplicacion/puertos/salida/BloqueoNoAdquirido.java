package com.roshka.challenge.pedidos.aplicacion.puertos.salida;

public class BloqueoNoAdquirido extends RuntimeException {

  private final String nombreBloqueo;

  public BloqueoNoAdquirido(String nombreBloqueo) {
    super("El bloqueo " + nombreBloqueo + " lo retiene otra solicitud");
    this.nombreBloqueo = nombreBloqueo;
  }

  public String nombreBloqueo() {
    return nombreBloqueo;
  }
}
