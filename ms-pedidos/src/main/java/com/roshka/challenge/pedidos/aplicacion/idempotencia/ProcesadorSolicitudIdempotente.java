package com.roshka.challenge.pedidos.aplicacion.idempotencia;

import com.roshka.challenge.pedidos.aplicacion.BloqueosPedido;
import com.roshka.challenge.pedidos.aplicacion.Pasos;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.SolicitudesIdempotentes;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.AlmacenClavesIdempotencia;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.BloqueoDistribuido;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.BloqueoNoAdquirido;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Los bloqueos son la primera barrera contra duplicados concurrentes entre instancias; la clave
 * primaria de la clave registrada es la garantía (ADR 0011). Los bloqueos envuelven la transacción,
 * así que nadie lee un estado que todavía no commiteó: primero el bloqueo de la clave, después los
 * de los recursos (p. ej. el pedido).
 */
@Service
public class ProcesadorSolicitudIdempotente implements SolicitudesIdempotentes {

  private static final Logger log = LoggerFactory.getLogger(ProcesadorSolicitudIdempotente.class);

  private final BloqueoDistribuido bloqueo;
  private final AlmacenClavesIdempotencia almacen;
  private final TransaccionIdempotente transaccion;
  private final ConfiguracionIdempotencia ajustes;
  private final Clock reloj;

  public ProcesadorSolicitudIdempotente(
      BloqueoDistribuido bloqueo,
      AlmacenClavesIdempotencia almacen,
      TransaccionIdempotente transaccion,
      ConfiguracionIdempotencia ajustes,
      Clock reloj) {
    this.bloqueo = bloqueo;
    this.almacen = almacen;
    this.transaccion = transaccion;
    this.ajustes = ajustes;
    this.reloj = reloj;
  }

  @Override
  public RespuestaIdempotente ejecutar(
      SolicitudIdempotente solicitud,
      List<String> bloqueosRecurso,
      Supplier<RespuestaAlmacenada> accion) {
    var bloqueoClave = ajustes.prefijoBloqueo() + solicitud.clave();
    try {
      return bloqueo.llamarConBloqueo(
          bloqueoClave,
          ajustes.esperaBloqueo(),
          ajustes.leaseBloqueo(),
          () -> conBloqueosRecurso(bloqueosRecurso, () -> procesar(solicitud, accion)));
    } catch (BloqueoNoAdquirido e) {
      if (e.nombreBloqueo().equals(bloqueoClave)) {
        throw new ClaveIdempotenciaEnProceso(ajustes.reintentarDespues());
      }
      throw new ModificacionConcurrente(ajustes.reintentarDespues());
    }
  }

  @Override
  public RespuestaIdempotente ejecutarEnPedido(
      SolicitudIdempotente solicitud, UUID idPedido, Supplier<RespuestaAlmacenada> accion) {
    return ejecutar(solicitud, List.of(BloqueosPedido.nombrePara(idPedido)), accion);
  }

  private <T> T conBloqueosRecurso(List<String> nombres, Supplier<T> accion) {
    if (nombres.isEmpty()) {
      return accion.get();
    }
    return bloqueo.llamarConBloqueo(
        nombres.getFirst(),
        ajustes.esperaBloqueoRecurso(),
        ajustes.leaseBloqueo(),
        () -> conBloqueosRecurso(nombres.subList(1, nombres.size()), accion));
  }

  private RespuestaIdempotente procesar(
      SolicitudIdempotente solicitud, Supplier<RespuestaAlmacenada> accion) {
    var pasos =
        Pasos.proceso(ProcesadorSolicitudIdempotente.class, "Solicitud idempotente")
            .con("idempotencyKey", solicitud.clave());
    return pasos.ejecutar(
        () -> {
          var previa = pasos.medir("Clave de idempotencia consultada", () -> registrado(solicitud));
          if (previa.isPresent()) {
            log.atInfo()
                .addKeyValue("idempotencyKey", solicitud.clave())
                .addKeyValue("status", previa.get().respuestaHttp().estado())
                .log(
                    "Solicitud repetida: se reprodujo la respuesta almacenada con la clave %s (estado %d), sin volver a ejecutarla"
                        .formatted(solicitud.clave(), previa.get().respuestaHttp().estado()));
            return previa.get();
          }
          return pasos.medir(
              "Solicitud ejecutada y respuesta registrada",
              () -> {
                try {
                  return transaccion.reclamarYEjecutar(solicitud, accion);
                } catch (TransaccionIdempotente.ClaveReclamadaConcurrentemente e) {
                  return registrado(solicitud)
                      .orElseThrow(
                          () -> new ClaveIdempotenciaEnProceso(ajustes.reintentarDespues()));
                }
              });
        });
  }

  private Optional<RespuestaIdempotente> registrado(SolicitudIdempotente solicitud) {
    return almacen
        .buscarValido(solicitud.clave(), reloj.instant())
        .map(
            clave -> {
              if (!clave.huella().equals(solicitud.huella())) {
                throw new ClaveIdempotenciaReutilizada();
              }
              return new RespuestaIdempotente(clave.respuestaHttp(), true);
            });
  }
}
