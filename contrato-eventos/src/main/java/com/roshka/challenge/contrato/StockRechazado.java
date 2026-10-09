package com.roshka.challenge.contrato;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record StockRechazado(UUID idPedido, MotivoRechazoStock motivo, List<UUID> idsProducto) {

  public StockRechazado {
    Objects.requireNonNull(idPedido, "idPedido");
    Objects.requireNonNull(motivo, "motivo");
    idsProducto = List.copyOf(idsProducto);
  }
}
