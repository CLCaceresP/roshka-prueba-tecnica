package com.roshka.challenge.pedidos.aplicacion.saga;

import com.roshka.challenge.contrato.MotivoRechazoStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.StockLiberado;
import com.roshka.challenge.contrato.StockRechazado;
import com.roshka.challenge.contrato.StockReservado;
import com.roshka.challenge.pedidos.aplicacion.BloqueosPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ManejarRespuestaInventario;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ResultadoManejo;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.BloqueoDistribuido;
import com.roshka.challenge.pedidos.dominio.MotivoRechazoPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;

/**
 * Aplica las respuestas de inventario bajo el bloqueo del pedido, que envuelve la transacción para
 * que una cancelación concurrente nunca se intercale con una respuesta (ADR 0005). Si no se obtiene
 * el bloqueo, se reintenta.
 */
@Service
public class RespuestaInventarioHandler implements ManejarRespuestaInventario {

  private final BloqueoDistribuido bloqueo;
  private final PasosSaga pasos;
  private final ConfiguracionSaga ajustes;

  public RespuestaInventarioHandler(
      BloqueoDistribuido bloqueo, PasosSaga pasos, ConfiguracionSaga ajustes) {
    this.bloqueo = bloqueo;
    this.pasos = pasos;
    this.ajustes = ajustes;
  }

  @Override
  public ResultadoManejo alStockReservado(SobreMensaje<StockReservado> evento) {
    return bajoBloqueoPedido(evento, evento.carga().idPedido(), Pedido::confirmarReserva);
  }

  @Override
  public ResultadoManejo alStockRechazado(SobreMensaje<StockRechazado> evento) {
    var motivo = aDominio(evento.carga().motivo());
    return bajoBloqueoPedido(
        evento,
        evento.carga().idPedido(),
        (pedido, ahora) -> pedido.rechazarReserva(motivo, ahora));
  }

  /** La liberación la solicitó este servicio: no hay nada que decidir, solo se deduplica. */
  @Override
  public ResultadoManejo alStockLiberado(SobreMensaje<StockLiberado> evento) {
    return bajoBloqueoPedido(evento, evento.carga().idPedido(), (pedido, ahora) -> {});
  }

  private static MotivoRechazoPedido aDominio(MotivoRechazoStock motivo) {
    return switch (motivo) {
      case STOCK_INSUFICIENTE -> MotivoRechazoPedido.INSUFFICIENT_STOCK;
      case PRODUCTO_DESCONOCIDO -> MotivoRechazoPedido.UNKNOWN_PRODUCT;
      case RESERVA_LIBERADA -> MotivoRechazoPedido.RESERVATION_RELEASED;
    };
  }

  private ResultadoManejo bajoBloqueoPedido(
      SobreMensaje<?> evento, UUID idPedido, BiConsumer<Pedido, Instant> transicion) {
    return bloqueo.llamarConBloqueo(
        BloqueosPedido.nombrePara(idPedido),
        ajustes.esperaBloqueoPedido(),
        ajustes.leaseBloqueoPedido(),
        () -> pasos.aplicar(evento, idPedido, transicion));
  }
}
