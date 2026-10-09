package com.roshka.challenge.inventario.aplicacion.idempotencia;

import com.roshka.challenge.inventario.aplicacion.puertos.salida.AlmacenClavesIdempotencia;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reclama la clave, ejecuta el caso de uso y registra su respuesta, todo o nada. */
@Service
public class TransaccionIdempotente {

  private final AlmacenClavesIdempotencia almacen;
  private final ConfiguracionIdempotencia ajustes;
  private final Clock reloj;

  public TransaccionIdempotente(
      AlmacenClavesIdempotencia almacen, ConfiguracionIdempotencia ajustes, Clock reloj) {
    this.almacen = almacen;
    this.ajustes = ajustes;
    this.reloj = reloj;
  }

  @Transactional
  public RespuestaIdempotente reclamarYEjecutar(
      SolicitudIdempotente solicitud, Supplier<RespuestaAlmacenada> accion) {
    var ahora = reloj.instant();
    if (!almacen.reclamar(
        solicitud.clave(), solicitud.huella(), ahora, ahora.plus(ajustes.ttl()))) {
      throw new ClaveReclamadaConcurrentemente();
    }
    var respuestaHttp = accion.get();
    almacen.completar(solicitud.clave(), respuestaHttp);
    return new RespuestaIdempotente(respuestaHttp, false);
  }

  /** Otra transacción commiteó antes la misma clave; esta se revierte y reproduce. */
  static class ClaveReclamadaConcurrentemente extends RuntimeException {}
}
