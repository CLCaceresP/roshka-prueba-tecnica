package com.roshka.challenge.contrato;

import java.util.Objects;
import java.util.UUID;

public record LiberarStock(UUID idPedido, MotivoLiberacion motivo) {

  public LiberarStock {
    Objects.requireNonNull(idPedido, "idPedido");
    Objects.requireNonNull(motivo, "motivo");
  }
}
