package com.roshka.challenge.pedidos.aplicacion;

import com.roshka.challenge.contrato.MotivoRechazoStock;
import com.roshka.challenge.contrato.ResultadoLiberacion;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.StockLiberado;
import com.roshka.challenge.contrato.StockRechazado;
import com.roshka.challenge.contrato.StockReservado;
import com.roshka.challenge.contrato.TiposMensaje;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Constructores de respuestas de inventario tal como las publicaría ms-inventario. */
public final class Respuestas {

  private Respuestas() {}

  public static SobreMensaje<StockReservado> stockReservado(UUID idPedido) {
    return sobre(idPedido, TiposMensaje.STOCK_RESERVADO, new StockReservado(idPedido, List.of()));
  }

  public static SobreMensaje<StockRechazado> stockRechazado(
      UUID idPedido, MotivoRechazoStock motivo) {
    return sobre(
        idPedido, TiposMensaje.STOCK_RECHAZADO, new StockRechazado(idPedido, motivo, List.of()));
  }

  public static SobreMensaje<StockLiberado> stockLiberado(UUID idPedido) {
    return sobre(
        idPedido,
        TiposMensaje.STOCK_LIBERADO,
        new StockLiberado(idPedido, ResultadoLiberacion.DEVUELTO));
  }

  private static <P> SobreMensaje<P> sobre(UUID idPedido, String tipo, P carga) {
    return new SobreMensaje<>(
        UUID.randomUUID(),
        tipo,
        TiposMensaje.VERSION_ESQUEMA,
        Instant.parse("2026-10-09T12:00:02Z"),
        "corr-reply",
        UUID.randomUUID(),
        idPedido,
        carga);
  }
}
