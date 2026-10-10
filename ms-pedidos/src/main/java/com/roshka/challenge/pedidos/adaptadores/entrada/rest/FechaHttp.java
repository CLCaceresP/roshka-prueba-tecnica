package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import com.roshka.challenge.pedidos.dominio.ParametroInvalido;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** Interpreta las fechas de la API, que viajan como {@code MM/dd/yyyy}. */
final class FechaHttp {

  private static final DateTimeFormatter FORMATO =
      DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);

  private FechaHttp() {}

  /**
   * @return la fecha, o {@code null} si el parámetro no vino o está en blanco
   * @throws ParametroInvalido si el valor no tiene el formato o no es una fecha real
   */
  static LocalDate parsear(String parametro, String valor) {
    if (valor == null || valor.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(valor.strip(), FORMATO);
    } catch (DateTimeParseException e) {
      throw new ParametroInvalido(
          "El parámetro %s debe tener formato MM/dd/yyyy y ser una fecha válida (por ejemplo 10/01/2026)"
              .formatted(parametro));
    }
  }
}
