package com.roshka.challenge.pedidos.aplicacion;

import com.roshka.challenge.pedidos.dominio.LineaPedido;
import java.util.List;
import java.util.stream.Collectors;

/** Describe las líneas de un pedido en una frase corta para los logs. */
public final class LineasLegibles {

  private LineasLegibles() {}

  public static String descripcion(List<LineaPedido> lineas) {
    return lineas.stream()
        .map(linea -> linea.idProducto() + "×" + linea.cantidad())
        .collect(Collectors.joining(", "));
  }

  public static int unidades(List<LineaPedido> lineas) {
    return lineas.stream().mapToInt(LineaPedido::cantidad).sum();
  }
}
