package com.roshka.challenge.inventario.aplicacion.puertos.entrada;

import com.roshka.challenge.inventario.aplicacion.consulta.Pagina;
import com.roshka.challenge.inventario.dominio.MovimientoStock;
import java.util.UUID;

public interface ListarMovimientos {

  /**
   * @throws com.roshka.challenge.inventario.dominio.ProductoNoEncontrado si el producto no existe
   */
  Pagina<MovimientoStock> listar(UUID idProducto, Integer pagina, Integer tamanio);
}
