package com.roshka.challenge.pedidos.dominio;

import java.util.UUID;

public class LineaProductoDuplicada extends PedidoInvalido {

  public LineaProductoDuplicada(UUID idProducto) {
    super("El producto " + idProducto + " aparece en más de una línea");
  }
}
