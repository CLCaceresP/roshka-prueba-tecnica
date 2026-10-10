package com.roshka.challenge.inventario.adaptadores.entrada.mensajeria;

/** Un registro que no cumple el contrato de mensajes; reintentar no lo arregla (ADR 0006). */
public class MensajeMalformado extends RuntimeException {

  public MensajeMalformado(String mensaje, Throwable causa) {
    super(mensaje, causa);
  }
}
