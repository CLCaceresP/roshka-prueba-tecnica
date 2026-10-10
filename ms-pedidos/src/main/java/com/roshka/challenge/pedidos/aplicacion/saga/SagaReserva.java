package com.roshka.challenge.pedidos.aplicacion.saga;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.LineaStock;
import com.roshka.challenge.contrato.MotivoLiberacion;
import com.roshka.challenge.contrato.ReservarStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.pedidos.aplicacion.AtributosLog;
import com.roshka.challenge.pedidos.aplicacion.LineasLegibles;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.MetricasPedidos;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.OutboxMensajes;
import com.roshka.challenge.pedidos.dominio.CausaLiberacion;
import com.roshka.challenge.pedidos.dominio.EventoPedido;
import com.roshka.challenge.pedidos.dominio.EventoPedido.LiberacionStockSolicitada;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoCancelado;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoColocado;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoConfirmado;
import com.roshka.challenge.pedidos.dominio.EventoPedido.PedidoRechazado;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.MotivoRechazoPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orquestador de la saga (ADR 0004): decide qué comando requiere cada evento de dominio y lo
 * registra en el outbox de la transacción actual. Nunca habla con Kafka.
 */
@Service
public class SagaReserva {

  private static final Logger log = LoggerFactory.getLogger(SagaReserva.class);

  private final OutboxMensajes outbox;
  private final MetricasPedidos metricas;
  private final Clock reloj;

  public SagaReserva(OutboxMensajes outbox, MetricasPedidos metricas, Clock reloj) {
    this.outbox = outbox;
    this.metricas = metricas;
    this.reloj = reloj;
  }

  public void despachar(
      Pedido pedido, List<EventoPedido> eventos, String idCorrelacion, UUID idCausa) {
    for (var evento : eventos) {
      switch (evento) {
        case PedidoColocado colocado -> {
          var items = descripcion(colocado.lineas());
          var unidades = unidades(colocado.lineas());
          AtributosLog.conPedido(log.atInfo(), colocado.idPedido())
              .addKeyValue("lines", colocado.lineas().size())
              .addKeyValue("units", unidades)
              .addKeyValue("items", items)
              .log(
                  "Pedido %s colocado: %d líneas, %d unidades (%s)"
                      .formatted(colocado.idPedido(), colocado.lineas().size(), unidades, items));
          metricas.pedidoColocado();
          enviar(
              TiposMensaje.RESERVAR_STOCK,
              colocado.idPedido(),
              new ReservarStock(
                  colocado.idPedido(),
                  colocado.lineas().stream()
                      .map(linea -> new LineaStock(linea.idProducto(), linea.cantidad()))
                      .toList()),
              idCorrelacion,
              idCausa);
        }
        case LiberacionStockSolicitada liberar -> {
          AtributosLog.conPedido(log.atInfo(), liberar.idPedido())
              .addKeyValue("cause", liberar.causa().name())
              .log(
                  "Liberación de stock solicitada para el pedido %s por %s"
                      .formatted(liberar.idPedido(), causaLegible(liberar.causa())));
          enviar(
              TiposMensaje.LIBERAR_STOCK,
              liberar.idPedido(),
              new LiberarStock(
                  liberar.idPedido(), MotivoLiberacion.valueOf(liberar.causa().name())),
              idCorrelacion,
              idCausa);
        }
        // Son desenlaces, no comandos: no hay nada que pedir a inventario, solo que medir y
        // registrar.
        case PedidoConfirmado confirmado -> {
          var ms = duracionSaga(pedido);
          AtributosLog.conPedido(log.atInfo(), confirmado.idPedido())
              .addKeyValue("sagaDurationMs", ms)
              .log(
                  "Pedido %s confirmado en %d ms (saga completa; pidió %s)"
                      .formatted(confirmado.idPedido(), ms, descripcion(pedido.lineas())));
          medirTerminado(pedido);
        }
        case PedidoRechazado rechazado -> {
          var ms = duracionSaga(pedido);
          AtributosLog.conPedido(log.atInfo(), rechazado.idPedido())
              .addKeyValue("reason", rechazado.motivo().name())
              .addKeyValue("sagaDurationMs", ms)
              .log(
                  "Pedido %s rechazado: %s (pedía %s) tras %d ms"
                      .formatted(
                          rechazado.idPedido(),
                          motivoLegible(rechazado.motivo()),
                          descripcion(pedido.lineas()),
                          ms));
          medirTerminado(pedido);
        }
        case PedidoCancelado cancelado -> {
          var ms = duracionSaga(pedido);
          AtributosLog.conPedido(log.atInfo(), cancelado.idPedido())
              .addKeyValue("sagaDurationMs", ms)
              .log(
                  "Pedido %s cancelado (pedía %s) tras %d ms"
                      .formatted(cancelado.idPedido(), descripcion(pedido.lineas()), ms));
          medirTerminado(pedido);
        }
      }
    }
  }

  private long duracionSaga(Pedido pedido) {
    return Duration.between(pedido.sagaIniciadaEn(), reloj.instant()).toMillis();
  }

  private static String descripcion(List<LineaPedido> lineas) {
    return LineasLegibles.descripcion(lineas);
  }

  private static int unidades(List<LineaPedido> lineas) {
    return LineasLegibles.unidades(lineas);
  }

  private static String motivoLegible(MotivoRechazoPedido motivo) {
    return switch (motivo) {
      case INSUFFICIENT_STOCK -> "stock insuficiente";
      case UNKNOWN_PRODUCT -> "producto inexistente";
      case RESERVATION_RELEASED -> "la reserva ya había sido liberada";
      case TIMEOUT -> "se agotó el tiempo de la saga";
    };
  }

  private static String causaLegible(CausaLiberacion causa) {
    return switch (causa) {
      case CANCELADO_POR_CLIENTE -> "cancelación del cliente";
      case TIMEOUT_SAGA -> "timeout de la saga";
      case COMPENSACION_RESERVA_TARDIA -> "reserva tardía sobre un pedido terminal";
    };
  }

  private void medirTerminado(Pedido pedido) {
    metricas.pedidoTerminado(
        pedido.estado(),
        pedido.motivoRechazo(),
        Duration.between(pedido.sagaIniciadaEn(), reloj.instant()));
  }

  private void enviar(
      String tipo, UUID idPedido, Object carga, String idCorrelacion, UUID idCausa) {
    outbox.agregar(
        Topicos.INVENTARIO_COMANDOS,
        new SobreMensaje<>(
            UUID.randomUUID(),
            tipo,
            TiposMensaje.VERSION_ESQUEMA,
            reloj.instant(),
            idCorrelacion,
            idCausa,
            idPedido,
            carga));
    AtributosLog.conPedido(log.atInfo(), idPedido)
        .addKeyValue("messageType", tipo)
        .addKeyValue("topic", Topicos.INVENTARIO_COMANDOS)
        .log(
            "Comando %s registrado en el outbox para el pedido %s (se publicará en %s)"
                .formatted(tipo, idPedido, Topicos.INVENTARIO_COMANDOS));
  }
}
