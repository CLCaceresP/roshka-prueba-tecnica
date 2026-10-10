package com.roshka.challenge.pedidos.aplicacion.saga;

import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.pedidos.aplicacion.AtributosLog;
import com.roshka.challenge.pedidos.aplicacion.Pasos;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ResultadoManejo;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.MensajesProcesados;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.dominio.Pedido;
import com.roshka.challenge.pedidos.dominio.PedidoNoEncontrado;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Una transacción por entrada de la saga: la deduplicación, la transición, los comandos de
 * compensación y el incremento de versión del pedido commitean juntos (ADR 0003, ADR 0010). Quien
 * llama retiene el bloqueo del pedido.
 */
@Service
public class PasosSaga {

  private static final Logger log = LoggerFactory.getLogger(PasosSaga.class);

  private final PedidoRepository pedidos;
  private final MensajesProcesados mensajesProcesados;
  private final SagaReserva saga;
  private final Clock reloj;

  public PasosSaga(
      PedidoRepository pedidos,
      MensajesProcesados mensajesProcesados,
      SagaReserva saga,
      Clock reloj) {
    this.pedidos = pedidos;
    this.mensajesProcesados = mensajesProcesados;
    this.saga = saga;
    this.reloj = reloj;
  }

  @Transactional
  public ResultadoManejo aplicar(
      SobreMensaje<?> respuesta, UUID idPedido, BiConsumer<Pedido, Instant> transicion) {
    var pasos =
        Pasos.proceso(PasosSaga.class, "Aplicar respuesta de inventario").con("orderId", idPedido);
    return pasos.ejecutar(
        () -> {
          var nuevo =
              pasos.medir(
                  "Mensaje marcado como procesado",
                  () -> mensajesProcesados.marcarProcesado(respuesta.idMensaje()));
          if (!nuevo) {
            AtributosLog.con(log.atInfo(), "messageId", respuesta.idMensaje().toString())
                .log(
                    "Mensaje %s duplicado: ya estaba procesado, no se vuelve a aplicar"
                        .formatted(respuesta.idMensaje()));
            return ResultadoManejo.DUPLICADO;
          }
          var pedido =
              pasos.medir(
                  "Pedido cargado",
                  () ->
                      pedidos
                          .buscarPorId(idPedido)
                          .orElseThrow(() -> new PedidoNoEncontrado(idPedido)));
          var antes = pedido.estado();
          pasos.medir("Transición aplicada", () -> transicion.accept(pedido, reloj.instant()));
          AtributosLog.conPedido(log.atInfo(), idPedido)
              .addKeyValue("fromStatus", antes.name())
              .addKeyValue("toStatus", pedido.estado().name())
              .log(
                  "Transición aplicada al pedido %s: %s → %s"
                      .formatted(idPedido, antes, pedido.estado()));
          pasos.medir(
              "Pedido actualizado y comandos registrados",
              () -> persistir(pedido, respuesta.idCorrelacion(), respuesta.idMensaje()));
          return ResultadoManejo.APLICADO;
        });
  }

  /**
   * @return si el pedido seguía PENDING y se expiró
   */
  @Transactional
  public boolean expirar(UUID idPedido) {
    var pasos = Pasos.proceso(PasosSaga.class, "Expirar pedido").con("orderId", idPedido);
    return pasos.ejecutar(
        () -> {
          var pedido =
              pasos.medir(
                  "Pedido cargado",
                  () ->
                      pedidos
                          .buscarPorId(idPedido)
                          .orElseThrow(() -> new PedidoNoEncontrado(idPedido)));
          pasos.medir("Expiración aplicada", () -> pedido.expirar(reloj.instant()));
          return pasos.medir(
              "Pedido actualizado y comandos registrados",
              () -> persistir(pedido, pedido.idCorrelacion(), null));
        });
  }

  private boolean persistir(Pedido pedido, String idCorrelacion, UUID idCausa) {
    var eventos = pedido.extraerEventos();
    if (eventos.isEmpty()) {
      return false;
    }
    saga.despachar(pedido, eventos, idCorrelacion, idCausa);
    pedidos.actualizar(pedido);
    return true;
  }
}
