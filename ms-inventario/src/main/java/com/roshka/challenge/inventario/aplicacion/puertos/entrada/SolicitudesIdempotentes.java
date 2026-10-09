package com.roshka.challenge.inventario.aplicacion.puertos.entrada;

import com.roshka.challenge.inventario.aplicacion.idempotencia.RespuestaAlmacenada;
import com.roshka.challenge.inventario.aplicacion.idempotencia.RespuestaIdempotente;
import com.roshka.challenge.inventario.aplicacion.idempotencia.SolicitudIdempotente;
import java.util.function.Supplier;

public interface SolicitudesIdempotentes {

  /**
   * Ejecuta la acción como máximo una vez por Idempotency-Key, en la misma transacción que registra
   * su respuesta, y reproduce esa respuesta para los duplicados (ADR 0011).
   *
   * @throws com.roshka.challenge.inventario.aplicacion.idempotencia.ClaveIdempotenciaReutilizada
   *     cuando la clave se usó en una solicitud distinta
   * @throws com.roshka.challenge.inventario.aplicacion.idempotencia.ClaveIdempotenciaEnProceso
   *     cuando una solicitud con la misma clave está en proceso
   */
  RespuestaIdempotente ejecutar(
      SolicitudIdempotente solicitud, Supplier<RespuestaAlmacenada> accion);
}
