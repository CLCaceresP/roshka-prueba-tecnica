package com.roshka.challenge.contrato;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReservarStock(UUID idPedido, List<LineaStock> lineas) {

  public ReservarStock {
    Objects.requireNonNull(idPedido, "idPedido");
    lineas = List.copyOf(lineas);
  }
}
