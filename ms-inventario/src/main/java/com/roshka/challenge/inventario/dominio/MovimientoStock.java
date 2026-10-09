package com.roshka.challenge.inventario.dominio;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Registro inmutable de un ajuste de stock, con el stock que quedó después de aplicarlo.
 *
 * @param motivo texto libre del ajuste; puede ser {@code null}
 */
public record MovimientoStock(
    UUID id,
    UUID idProducto,
    TipoAjuste tipo,
    int cantidad,
    String motivo,
    int totalDespues,
    int disponibleDespues,
    Instant creadoEn) {

  /**
   * El movimiento que lleva de {@code anterior} a {@code nuevo}: {@code ADD} si subió lo
   * disponible, {@code REMOVE} si bajó. Vacío si no cambió.
   */
  public static Optional<MovimientoStock> entre(
      Producto anterior, Producto nuevo, String motivo, Instant creadoEn) {
    var diferencia = nuevo.disponible() - anterior.disponible();
    if (diferencia == 0) {
      return Optional.empty();
    }
    return Optional.of(
        new MovimientoStock(
            UUID.randomUUID(),
            nuevo.id(),
            diferencia > 0 ? TipoAjuste.ADD : TipoAjuste.REMOVE,
            Math.abs(diferencia),
            motivo,
            nuevo.total(),
            nuevo.disponible(),
            creadoEn));
  }
}
