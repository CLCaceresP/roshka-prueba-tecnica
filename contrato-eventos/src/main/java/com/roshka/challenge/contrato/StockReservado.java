package com.roshka.challenge.contrato;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record StockReservado(UUID idPedido, List<LineaStock> lineas) {

  public StockReservado {
    Objects.requireNonNull(idPedido, "idPedido");
    lineas = List.copyOf(lineas);
  }
}
