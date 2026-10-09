package com.roshka.challenge.inventario.aplicacion.puertos.entrada;

import com.roshka.challenge.inventario.aplicacion.consulta.Pagina;
import com.roshka.challenge.inventario.dominio.Producto;

public interface ListarProductos {

  /**
   * @param pagina número de página desde 0; {@code null} para la primera
   * @param tamanio elementos por página; {@code null} para el tamaño por defecto
   * @throws com.roshka.challenge.inventario.dominio.ParametroInvalido si están fuera de los límites
   */
  Pagina<Producto> listar(Integer pagina, Integer tamanio);
}
