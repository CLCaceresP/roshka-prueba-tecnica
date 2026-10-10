package com.roshka.challenge.pedidos.aplicacion.consulta;

import com.roshka.challenge.pedidos.dominio.ParametroInvalido;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Rango de días completos e inclusivos, interpretados en la zona horaria configurada. */
public record RangoFechas(LocalDate desde, LocalDate hasta, ConfiguracionConsulta configuracion) {

  private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("MM/dd/uuuu");

  /**
   * Sin fechas se usa el día de hoy; con una sola, ese día.
   *
   * @throws ParametroInvalido si el inicio es posterior al fin o si el rango supera el máximo de
   *     días
   */
  public static RangoFechas de(
      LocalDate desde, LocalDate hasta, ConfiguracionConsulta configuracion, Clock reloj) {
    var inicio = desde;
    var fin = hasta;
    if (inicio == null && fin == null) {
      inicio = LocalDate.now(reloj.withZone(configuracion.zona()));
      fin = inicio;
    } else if (inicio == null) {
      inicio = fin;
    } else if (fin == null) {
      fin = inicio;
    }
    if (inicio.isAfter(fin)) {
      throw new ParametroInvalido("El parámetro from no puede ser posterior a to");
    }
    var dias = ChronoUnit.DAYS.between(inicio, fin) + 1;
    if (dias > configuracion.rangoMaximoDias()) {
      throw new ParametroInvalido(
          "El rango no puede abarcar más de %d días (se pidieron %d)"
              .formatted(configuracion.rangoMaximoDias(), dias));
    }
    return new RangoFechas(inicio, fin, configuracion);
  }

  /** Primer instante del rango (inclusive). */
  public Instant instanteDesde() {
    return desde.atStartOfDay(configuracion.zona()).toInstant();
  }

  /** Primer instante posterior al rango (exclusivo). */
  public Instant instanteHasta() {
    return hasta.plusDays(1).atStartOfDay(configuracion.zona()).toInstant();
  }

  public String descripcion() {
    return "del %s al %s".formatted(FORMATO.format(desde), FORMATO.format(hasta));
  }
}
