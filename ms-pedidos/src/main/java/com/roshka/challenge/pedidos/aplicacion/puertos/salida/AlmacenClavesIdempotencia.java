package com.roshka.challenge.pedidos.aplicacion.puertos.salida;

import com.roshka.challenge.pedidos.aplicacion.idempotencia.RespuestaAlmacenada;
import java.time.Instant;
import java.util.Optional;

public interface AlmacenClavesIdempotencia {

  /** Una clave registrada por una solicitud ya commiteada y todavía no vencida. */
  record ClaveRegistrada(String huella, RespuestaAlmacenada respuestaHttp) {}

  Optional<ClaveRegistrada> buscarValido(String clave, Instant ahora);

  /**
   * Reclama la clave en la transacción actual, o toma una vencida. Un reclamo concurrente de la
   * misma clave espera hasta que termina la primera transacción.
   *
   * @return {@code false} cuando otra solicitud ya commiteada es dueña de la clave
   */
  boolean reclamar(String clave, String huella, Instant ahora, Instant expiraEn);

  void completar(String clave, RespuestaAlmacenada respuestaHttp);
}
