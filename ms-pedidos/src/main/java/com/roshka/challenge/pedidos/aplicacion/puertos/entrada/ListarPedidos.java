package com.roshka.challenge.pedidos.aplicacion.puertos.entrada;

import com.roshka.challenge.pedidos.aplicacion.consulta.Pagina;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.time.LocalDate;

public interface ListarPedidos {

  /**
   * Pedidos creados entre {@code desde} y {@code hasta}, ambos inclusive. Sin fechas, los de hoy;
   * con una sola, ese día. Sin página ni tamaño, los valores por defecto.
   *
   * @throws com.roshka.challenge.pedidos.dominio.ParametroInvalido si el rango o la paginación
   *     están fuera de los límites
   */
  Pagina<Pedido> listar(LocalDate desde, LocalDate hasta, Integer pagina, Integer tamanio);
}
