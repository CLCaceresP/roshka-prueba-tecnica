package com.roshka.challenge.inventario.aplicacion.puertos.entrada;

import com.roshka.challenge.inventario.dominio.AjusteStock;
import com.roshka.challenge.inventario.dominio.MovimientoStock;
import com.roshka.challenge.inventario.dominio.Producto;

public interface AjustarStock {

  /**
   * @throws com.roshka.challenge.inventario.dominio.ProductoNoEncontrado si el producto no existe
   * @throws com.roshka.challenge.inventario.dominio.LimiteStockExcedido si el total superaría el
   *     máximo
   */
  Resultado ajustar(AjusteStock ajuste);

  /**
   * @param movimiento el cambio registrado; {@code null} si el stock ya tenía ese valor
   */
  record Resultado(Producto producto, MovimientoStock movimiento) {}
}
