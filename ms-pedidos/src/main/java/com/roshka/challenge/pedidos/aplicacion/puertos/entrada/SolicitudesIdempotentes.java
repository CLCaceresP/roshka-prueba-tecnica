package com.roshka.challenge.pedidos.aplicacion.puertos.entrada;

import com.roshka.challenge.pedidos.aplicacion.idempotencia.RespuestaAlmacenada;
import com.roshka.challenge.pedidos.aplicacion.idempotencia.RespuestaIdempotente;
import com.roshka.challenge.pedidos.aplicacion.idempotencia.SolicitudIdempotente;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public interface SolicitudesIdempotentes {

  /**
   * Ejecuta la acción como máximo una vez por Idempotency-Key, en la misma transacción que registra
   * su respuesta, y reproduce esa respuesta para los duplicados (ADR 0011). Los bloqueos de recurso
   * (p. ej. el del pedido) se toman después del bloqueo de la clave y se liberan después del
   * commit.
   *
   * @throws com.roshka.challenge.pedidos.aplicacion.idempotencia.ClaveIdempotenciaReutilizada
   *     cuando la clave se usó en una solicitud distinta
   * @throws com.roshka.challenge.pedidos.aplicacion.idempotencia.ClaveIdempotenciaEnProceso cuando
   *     una solicitud con la misma clave está en proceso
   * @throws com.roshka.challenge.pedidos.aplicacion.idempotencia.ModificacionConcurrente cuando
   *     otro retiene el bloqueo de un recurso
   */
  RespuestaIdempotente ejecutar(
      SolicitudIdempotente solicitud,
      List<String> bloqueosRecurso,
      Supplier<RespuestaAlmacenada> accion);

  /**
   * Igual que {@link #ejecutar} para una solicitud que modifica un pedido: el bloqueo del pedido se
   * toma después del bloqueo de la clave y se retiene hasta el commit, así que el cambio nunca se
   * intercala con un paso de la saga.
   */
  RespuestaIdempotente ejecutarEnPedido(
      SolicitudIdempotente solicitud, UUID idPedido, Supplier<RespuestaAlmacenada> accion);
}
