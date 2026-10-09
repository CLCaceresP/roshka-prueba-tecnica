package com.roshka.challenge.contrato;

import java.util.Objects;
import java.util.UUID;

public record LineaStock(UUID idProducto, int cantidad) {

  public LineaStock {
    Objects.requireNonNull(idProducto, "idProducto");
  }
}
