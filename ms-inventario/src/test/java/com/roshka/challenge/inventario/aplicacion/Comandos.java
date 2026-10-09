package com.roshka.challenge.inventario.aplicacion;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.LineaStock;
import com.roshka.challenge.contrato.MotivoLiberacion;
import com.roshka.challenge.contrato.ReservarStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.TiposMensaje;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Constructores de comandos de entrada tal como los publicaría ms-pedidos. */
final class Comandos {

  static final String ID_CORRELACION = "corr-test";

  private Comandos() {}

  static SobreMensaje<ReservarStock> reservar(UUID idPedido, LineaStock... lineas) {
    return new SobreMensaje<>(
        UUID.randomUUID(),
        TiposMensaje.RESERVAR_STOCK,
        TiposMensaje.VERSION_ESQUEMA,
        Instant.parse("2026-10-09T12:00:00Z"),
        ID_CORRELACION,
        null,
        idPedido,
        new ReservarStock(idPedido, List.of(lineas)));
  }

  static SobreMensaje<LiberarStock> liberar(UUID idPedido) {
    return new SobreMensaje<>(
        UUID.randomUUID(),
        TiposMensaje.LIBERAR_STOCK,
        TiposMensaje.VERSION_ESQUEMA,
        Instant.parse("2026-10-09T12:00:01Z"),
        ID_CORRELACION,
        null,
        idPedido,
        new LiberarStock(idPedido, MotivoLiberacion.CANCELADO_POR_CLIENTE));
  }

  static LineaStock linea(UUID idProducto, int cantidad) {
    return new LineaStock(idProducto, cantidad);
  }
}
