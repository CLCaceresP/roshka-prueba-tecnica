package com.roshka.challenge.inventario.aplicacion.puertos.entrada;

import com.roshka.challenge.inventario.dominio.Producto;
import java.util.UUID;

public interface ObtenerStockProducto {

  /**
   * @throws com.roshka.challenge.inventario.dominio.ProductoNoEncontrado cuando el producto no
   *     existe
   */
  Producto stockDe(UUID idProducto);
}
