package com.roshka.challenge.inventario.aplicacion.puertos.salida;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Exclusión mutua entre instancias. Solo defensa en profundidad: la corrección nunca depende de
 * ella (ADR 0005). Nunca bloquea indefinidamente.
 */
public interface BloqueoDistribuido {

  /**
   * Ejecuta la acción reteniendo el bloqueo con ese nombre, que se libera después aunque la acción
   * falle.
   *
   * @throws BloqueoNoAdquirido cuando el bloqueo no se obtiene dentro de {@code espera}
   * @throws BloqueoNoDisponible cuando no se puede llegar al servicio de bloqueo
   */
  <T> T llamarConBloqueo(String nombre, Duration espera, Duration lease, Supplier<T> accion);
}
