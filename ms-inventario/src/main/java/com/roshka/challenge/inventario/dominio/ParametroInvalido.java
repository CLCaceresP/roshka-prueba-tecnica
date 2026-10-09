package com.roshka.challenge.inventario.dominio;

/** Un parámetro de consulta está fuera de rango o mal formado. */
public class ParametroInvalido extends RuntimeException {

  public ParametroInvalido(String mensaje) {
    super(mensaje);
  }
}
