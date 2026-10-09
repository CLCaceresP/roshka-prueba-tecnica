package com.roshka.challenge.contrato;

import java.util.Objects;
import java.util.UUID;

public record StockLiberado(UUID idPedido, ResultadoLiberacion desenlace) {

  public StockLiberado {
    Objects.requireNonNull(idPedido, "idPedido");
    Objects.requireNonNull(desenlace, "desenlace");
  }
}
