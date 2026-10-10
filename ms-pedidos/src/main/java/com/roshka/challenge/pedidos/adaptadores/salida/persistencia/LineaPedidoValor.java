package com.roshka.challenge.pedidos.adaptadores.salida.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
class LineaPedidoValor {

  @Column(name = "product_id", nullable = false)
  private UUID idProducto;

  @Column(name = "quantity", nullable = false)
  private int cantidad;

  protected LineaPedidoValor() {}

  LineaPedidoValor(UUID idProducto, int cantidad) {
    this.idProducto = idProducto;
    this.cantidad = cantidad;
  }

  UUID idProducto() {
    return idProducto;
  }

  int cantidad() {
    return cantidad;
  }
}
