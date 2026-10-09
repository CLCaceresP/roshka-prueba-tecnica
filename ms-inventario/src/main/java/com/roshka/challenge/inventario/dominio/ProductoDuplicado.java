package com.roshka.challenge.inventario.dominio;

import java.util.UUID;

/** Ya existe un producto cuyo nombre normalizado coincide, sin distinguir mayúsculas. */
public class ProductoDuplicado extends RuntimeException {

  private final String nombre;
  private final UUID idExistente;

  public ProductoDuplicado(String nombre, UUID idExistente) {
    super(
        idExistente == null
            ? "Ya existe un producto con el nombre «" + nombre + "»"
            : "Ya existe el producto " + idExistente + " con el nombre «" + nombre + "»");
    this.nombre = nombre;
    this.idExistente = idExistente;
  }

  public String nombre() {
    return nombre;
  }

  /** Puede ser {@code null} cuando el duplicado se detectó por una carrera con otra solicitud. */
  public UUID idExistente() {
    return idExistente;
  }
}
