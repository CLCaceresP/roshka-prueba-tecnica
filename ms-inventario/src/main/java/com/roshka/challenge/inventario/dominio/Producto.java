package com.roshka.challenge.inventario.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Stock de un producto. Siempre se cumple {@code disponible + reservado = total}; las reservas
 * mueven unidades de disponible a reservado y las liberaciones las devuelven (ver el ADR 0009 para
 * saber cómo la base de datos impone las mismas invariantes bajo concurrencia).
 */
public record Producto(UUID id, String nombre, int total, int disponible, int reservado) {

  public static final int LONGITUD_MAXIMA_NOMBRE = 120;
  public static final int CANTIDAD_INICIAL_MAXIMA = 1_000_000;
  public static final int TOTAL_MAXIMO = 10_000_000;

  public Producto {
    Objects.requireNonNull(id, "id");
    if (nombre == null || nombre.isBlank() || nombre.length() > LONGITUD_MAXIMA_NOMBRE) {
      throw new ProductoInvalido(
          "nombre debe tener entre 1 y " + LONGITUD_MAXIMA_NOMBRE + " caracteres");
    }
    if (disponible < 0 || reservado < 0) {
      throw new ProductoInvalido("available and reserved stock cannot be negative");
    }
    if (disponible + reservado != total) {
      throw new ProductoInvalido("disponible + reservado debe ser igual a total");
    }
  }

  /**
   * El producto con {@code disponible} unidades disponibles. Lo reservado no cambia y el total pasa
   * a ser disponible + reservado.
   *
   * @throws LimiteStockExcedido si el total superaría {@link #TOTAL_MAXIMO}
   */
  public Producto conDisponible(int disponible) {
    if ((long) disponible + reservado > TOTAL_MAXIMO) {
      throw new LimiteStockExcedido(id, reservado, disponible);
    }
    return new Producto(id, nombre, disponible + reservado, disponible, reservado);
  }

  public static Producto registrar(UUID id, String nombre, int cantidadInicial) {
    if (cantidadInicial < 0 || cantidadInicial > CANTIDAD_INICIAL_MAXIMA) {
      throw new ProductoInvalido("cantidadInicial debe estar entre 0 y " + CANTIDAD_INICIAL_MAXIMA);
    }
    return new Producto(id, normalizar(nombre), cantidadInicial, cantidadInicial, 0);
  }

  /**
   * Recorta los espacios de los extremos y reduce los espacios internos repetidos a uno. Rechaza
   * los caracteres de control que no son espacio en blanco.
   */
  static String normalizar(String nombre) {
    return normalizar(nombre, "nombre");
  }

  static String normalizar(String nombre, String campo) {
    if (nombre == null) {
      return null;
    }
    if (nombre
        .codePoints()
        .anyMatch(c -> Character.isISOControl(c) && !Character.isWhitespace(c))) {
      throw new ProductoInvalido(campo + " no puede tener caracteres de control");
    }
    return nombre.strip().replaceAll("\\s+", " ");
  }
}
