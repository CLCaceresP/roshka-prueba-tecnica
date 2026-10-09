package com.roshka.challenge.inventario;

import java.util.UUID;

/** Nombres de producto distintos en cada llamada: el nombre es único en la base (spec 011). */
public final class NombresUnicos {

  private NombresUnicos() {}

  public static String de(String base) {
    return base + "-" + UUID.randomUUID();
  }
}
